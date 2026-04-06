package com.openclaw.android.llm

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import java.io.File

/**
 * Singleton bridge to the LiteRT-LM inference engine.
 *
 * Replaces the old LlamaBridge (JNI → llama.cpp) with Google's production-grade
 * LiteRT-LM SDK. Key differences:
 *
 *  - No native C++/JNI code — pure Kotlin via the Maven artifact
 *  - Uses `.litertlm` model files (quantized, optimized for on-device)
 *  - Engine handles tokenization, KV-cache, hardware acceleration internally
 *  - Streaming via Kotlin Flow (not raw token callbacks)
 *  - Supports CPU, GPU, and NPU backends natively
 *
 * Architecture (from Google's docs):
 *  - Engine (singleton): Loads the model once. Shared across the app.
 *  - Conversation: Stateful chat session created from the engine.
 *    Manages chat history, context window, and turn-taking internally.
 */
object LiteRTBridge {

    private const val TAG = "LiteRTBridge"

    /** The LiteRT-LM engine instance. Null if no model is loaded. */
    private var engine: Engine? = null

    /** The active conversation session. */
    private var conversation: Conversation? = null

    /** Path of the currently loaded model. */
    private var loadedModelPath: String? = null

    /** Whether a model is currently loaded and ready for inference. */
    val isModelLoaded: Boolean
        get() = engine != null

    /** Always true — LiteRT-LM is a pure Kotlin/Maven dependency, no native stub issues. */
    val isAvailable: Boolean = true

    /**
     * Load a .litertlm model file.
     *
     * This can take 5-10+ seconds on first load (model weights are rearranged
     * for optimal execution on the specific device). Subsequent loads are faster
     * because the optimized weights are cached.
     *
     * @param modelPath Absolute path to the .litertlm file
     * @param backend Which hardware backend to use (CPU, GPU)
     * @return Result.success if loaded, Result.failure with details if not
     */
    fun loadModel(
        modelPath: String,
        backend: Backend = Backend.CPU(),
    ): Result<Unit> {
        val file = File(modelPath)
        if (!file.exists() || file.length() < 100) {
            return Result.failure(IllegalArgumentException("Model file missing or too small: $modelPath"))
        }

        // Unload any previously loaded model
        unloadModel()

        return try {
            // Suppress verbose native logs in the TUI/chat experience
            Engine.setNativeMinLogSeverity(LogSeverity.ERROR)

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = backend,
            )

            val newEngine = Engine(engineConfig)
            newEngine.initialize()

            engine = newEngine
            loadedModelPath = modelPath
            Log.i(TAG, "Model loaded: $modelPath (backend=${backend::class.simpleName})")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: $modelPath", e)
            engine = null
            loadedModelPath = null
            Result.failure(e)
        }
    }

    /**
     * Create a new conversation session with optional configuration.
     *
     * Each conversation maintains its own KV-cache and chat history internally.
     * Call this before [sendMessage]. Old conversation is automatically closed.
     */
    fun createConversation(
        systemInstruction: String? = null,
        temperature: Float = 0.7f,
        topK: Int = 40,
        maxTokens: Int = 2048,
    ): Result<Unit> {
        val eng = engine ?: return Result.failure(
            IllegalStateException("No model loaded. Call loadModel() first.")
        )

        // Close previous conversation if any
        conversation?.close()
        conversation = null

        return try {
            val samplerConfig = SamplerConfig(
                topK = topK,
                topP = 0.95,  // alpha02 only supports topK and topP; temperature not available in this version
            )

            val config = if (systemInstruction != null) {
                ConversationConfig(
                    samplerConfig = samplerConfig,
                    systemInstruction = com.google.ai.edge.litertlm.Contents.of(systemInstruction)
                )
            } else {
                ConversationConfig(samplerConfig = samplerConfig)
            }

            conversation = eng.createConversation(config)
            Log.i(TAG, "Conversation created (temp=$temperature, topK=$topK)")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create conversation", e)
            Result.failure(e)
        }
    }

    /**
     * Send a message and stream the response token by token.
     *
     * This is the core inference method. LiteRT-LM handles:
     *  - Tokenization
     *  - KV-cache management
     *  - Context window management
     *  - Hardware-accelerated decode
     *
     * @param message The user's message text
     * @param onToken Called for each streamed token. Return false to cancel.
     * @param onDone Called when generation is complete with the full response.
     * @param onError Called if an error occurs during generation.
     */
    suspend fun sendMessage(
        message: String,
        onToken: (String) -> Boolean,
        onDone: (String) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val conv = conversation ?: run {
            onError(IllegalStateException("No conversation. Call createConversation() first."))
            return
        }

        try {
            val fullResponse = StringBuilder()

            conv.sendMessageAsync(message)
                .catch { e ->
                    Log.e(TAG, "Stream error", e)
                    onError(if (e is Exception) e else RuntimeException(e.message, e))
                }
                .onCompletion {
                    if (it == null) {
                        onDone(fullResponse.toString())
                    }
                }
                .collect { chunk ->
                    val tokenText = chunk.toString()
                    fullResponse.append(tokenText)
                    val shouldContinue = onToken(tokenText)
                    if (!shouldContinue) {
                        // Note: Flow cancellation is handled by the coroutine scope
                        return@collect
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessage error", e)
            onError(e)
        }
    }

    /** Unload the current model and free all resources. */
    fun unloadModel() {
        try {
            conversation?.close()
            conversation = null
            engine?.close()
            engine = null
            loadedModelPath = null
            Log.i(TAG, "Model unloaded")
        } catch (e: Exception) {
            Log.w(TAG, "Error during unload", e)
            // Force cleanup
            conversation = null
            engine = null
            loadedModelPath = null
        }
    }

    /** Get the path of the currently loaded model, or null. */
    fun getLoadedModelPath(): String? = loadedModelPath

    /** Rough token count estimate (4 chars ≈ 1 token). */
    fun estimateTokenCount(text: String): Int = (text.length + 3) / 4

    /**
     * Get available RAM in MB using Android's MemoryInfo API.
     * Used for deciding whether a model can be loaded.
     */
    fun getAvailableMemoryMb(context: android.content.Context? = null): Long {
        // Try Android MemoryInfo first (most accurate)
        if (context != null) {
            try {
                val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                    as? android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                if (memInfo.availMem > 0) {
                    return memInfo.availMem / (1024 * 1024)
                }
            } catch (_: Exception) {}
        }

        // Fallback: /proc/meminfo
        try {
            val meminfo = File("/proc/meminfo")
            if (meminfo.exists()) {
                meminfo.useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("MemAvailable:")) {
                            val parts = line.split("\\s+".toRegex())
                            if (parts.size >= 2) {
                                return parts[1].toLong() / 1024 // kB to MB
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Last resort: JVM memory
        return Runtime.getRuntime().let {
            (it.maxMemory() - it.totalMemory() + it.freeMemory()) / (1024 * 1024)
        }
    }

    /**
     * Get total RAM in MB.
     */
    fun getTotalMemoryMb(context: android.content.Context? = null): Long {
        if (context != null) {
            try {
                val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                    as? android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                if (memInfo.totalMem > 0) {
                    return memInfo.totalMem / (1024 * 1024)
                }
            } catch (_: Exception) {}
        }

        try {
            val meminfo = File("/proc/meminfo")
            if (meminfo.exists()) {
                meminfo.useLines { lines ->
                    for (line in lines) {
                        if (line.startsWith("MemTotal:")) {
                            val parts = line.split("\\s+".toRegex())
                            if (parts.size >= 2) {
                                return parts[1].toLong() / 1024
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return Runtime.getRuntime().maxMemory() / (1024 * 1024)
    }
}
