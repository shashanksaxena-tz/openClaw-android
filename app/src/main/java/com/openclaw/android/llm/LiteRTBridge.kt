package com.openclaw.android.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

/**
 * Singleton bridge to the LiteRT-LM inference engine.
 *
 * Matches Google's Edge Gallery initialization pattern exactly:
 *  - EngineConfig with cacheDir for optimized weight storage
 *  - CPU backend by default (GPU opt-in to avoid native crashes on some devices)
 *  - MessageCallback for streaming (not Flow<Message>.toString())
 *  - Proper Content.Text extraction from Message objects
 */
object LiteRTBridge {

    private const val TAG = "LiteRTBridge"

    /** The LiteRT-LM engine instance. Null if no model is loaded. */
    private var engine: Engine? = null

    /** The active conversation session. */
    private var conversation: Conversation? = null

    /** Path of the currently loaded model. */
    private var loadedModelPath: String? = null

    /** Android context for resolving cache directories. */
    private var appContext: Context? = null

    /** Whether a model is currently loaded and ready for inference. */
    val isModelLoaded: Boolean
        get() = engine != null

    /** Always true — LiteRT-LM is a pure Kotlin/Maven dependency, no native stub issues. */
    val isAvailable: Boolean = true

    /** Set the application context (call once from Application.onCreate). */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Load a .litertlm model file.
     *
     * Matches Edge Gallery's initialization pattern:
     *  - EngineConfig includes cacheDir for optimized weight caching
     *  - Engine(config) + engine.initialize()
     *  - CPU backend by default (safe on all devices)
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
            // Resolve cacheDir the same way Edge Gallery does
            val cacheDir = appContext?.getExternalFilesDir(null)?.absolutePath

            val engineConfig = EngineConfig(
                modelPath = modelPath,
                backend = backend,
                cacheDir = cacheDir,
            )

            Log.i(TAG, "Creating engine: model=$modelPath, backend=${backend::class.simpleName}, cacheDir=$cacheDir")
            val newEngine = Engine(engineConfig)
            newEngine.initialize()

            engine = newEngine
            loadedModelPath = modelPath
            Log.i(TAG, "Model loaded successfully: $modelPath")
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
     * Matches Edge Gallery's pattern: engine.createConversation(ConversationConfig(...))
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
                topP = 0.95,
                temperature = temperature.toDouble(),
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
     * Uses the MessageCallback pattern (same as Edge Gallery) instead of
     * Flow<Message>.toString() which doesn't extract text properly.
     *
     * @param message The user's message text
     * @param onToken Called for each streamed token text.
     * @param onDone Called when generation is complete with the full response.
     * @param onError Called if an error occurs during generation.
     */
    fun sendMessage(
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

            // Use MessageCallback pattern exactly like Edge Gallery
            conv.sendMessageAsync(
                Message.user(message),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        // Extract text content from Message, same as Edge Gallery
                        message.contents.filterIsInstance<Content.Text>().forEach { textContent ->
                            val text = textContent.text
                            fullResponse.append(text)
                            onToken(text)
                        }
                    }

                    override fun onDone() {
                        onDone(fullResponse.toString())
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Stream error", throwable)
                        val ex = if (throwable is Exception) throwable
                                 else RuntimeException(throwable.message, throwable)
                        onError(ex)
                    }
                },
            )
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
