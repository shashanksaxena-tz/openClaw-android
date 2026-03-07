package com.openclaw.android.llm

import android.util.Log

/**
 * JNI bridge to llama.cpp native library.
 *
 * Provides low-level access to GGUF model loading, tokenization,
 * and text generation. All heavy lifting happens in native C++ code
 * via llama.cpp compiled for Android (arm64-v8a / armeabi-v7a / x86_64).
 *
 * Usage:
 *   1. Call [loadModel] with a path to a .gguf file
 *   2. Call [generate] with a prompt to get streaming text output
 *   3. Call [unloadModel] when done or switching models
 */
object LlamaBridge {

    private const val TAG = "LlamaBridge"

    /** Whether the native library was loaded successfully. */
    var isLoaded: Boolean = false
        private set

    /**
     * Whether this is a real build with llama.cpp compiled in (not a stub).
     * When false, model loading and inference will always fail.
     */
    var isRealBuild: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("llama_bridge")
            isLoaded = true
            isRealBuild = try { nativeIsRealBuild() } catch (e: Exception) { false }
            Log.i(TAG, "llama_bridge native library loaded (realBuild=$isRealBuild)")
        } catch (e: UnsatisfiedLinkError) {
            isLoaded = false
            isRealBuild = false
            Log.w(TAG, "llama_bridge native library not available: ${e.message}")
        }
    }

    // ── Native methods (implemented in llama_bridge.cpp) ─────────────────────

    /** Returns true if llama.cpp is compiled in (not a stub build). */
    external fun nativeIsRealBuild(): Boolean

    /**
     * Load a GGUF model from disk.
     *
     * @param modelPath absolute path to the .gguf file
     * @param nThreads number of CPU threads for inference (0 = auto-detect)
     * @param nGpuLayers number of layers to offload to GPU (0 = CPU only)
     * @param contextSize max context window size in tokens
     * @return true if model loaded successfully
     */
    external fun nativeLoadModel(
        modelPath: String,
        nThreads: Int,
        nGpuLayers: Int,
        contextSize: Int,
    ): Boolean

    /** Unload the currently loaded model and free memory. */
    external fun nativeUnloadModel()

    /** Check if a model is currently loaded. */
    external fun nativeIsModelLoaded(): Boolean

    /**
     * Generate text from a prompt with streaming callback.
     *
     * @param prompt the full prompt text (including system prompt and chat history)
     * @param maxTokens maximum tokens to generate
     * @param temperature sampling temperature (0.0 = greedy, higher = more random)
     * @param topP nucleus sampling threshold
     * @param topK top-k sampling (0 = disabled)
     * @param repeatPenalty penalty for repeating tokens
     * @param stopSequences list of strings that stop generation when encountered
     * @param callback called for each generated token; return false to stop generation
     * @return full generated text
     */
    external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        stopSequences: Array<String>,
        callback: GenerationCallback,
    ): String

    /** Get metadata about the loaded model (name, size, quant type, etc.). */
    external fun nativeGetModelInfo(): String

    /** Get the model's max context length. */
    external fun nativeGetContextLength(): Int

    /** Tokenize a string and return token count (useful for context management). */
    external fun nativeTokenCount(text: String): Int

    /** Get available system RAM in bytes. */
    external fun nativeGetAvailableMemory(): Long

    // ── Kotlin API (wraps native calls with safety checks) ───────────────────

    fun loadModel(
        modelPath: String,
        nThreads: Int = 0,
        nGpuLayers: Int = 0,
        contextSize: Int = 4096,
    ): Result<Unit> {
        if (!isLoaded) return Result.failure(
            IllegalStateException("Native library not loaded. Ensure llama_bridge.so is included in the APK.")
        )

        return try {
            if (nativeIsModelLoaded()) {
                nativeUnloadModel()
            }
            val success = nativeLoadModel(modelPath, nThreads, nGpuLayers, contextSize)
            if (success) {
                Log.i(TAG, "Model loaded: $modelPath")
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("Failed to load model: $modelPath"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
            Result.failure(e)
        }
    }

    fun unloadModel() {
        if (isLoaded && nativeIsModelLoaded()) {
            nativeUnloadModel()
            Log.i(TAG, "Model unloaded")
        }
    }

    fun isModelLoaded(): Boolean = isLoaded && nativeIsModelLoaded()

    fun generate(
        prompt: String,
        maxTokens: Int = 2048,
        temperature: Float = 0.7f,
        topP: Float = 0.9f,
        topK: Int = 40,
        repeatPenalty: Float = 1.1f,
        stopSequences: List<String> = emptyList(),
        onToken: (String) -> Boolean = { true }, // return false to stop
    ): Result<String> {
        if (!isLoaded) return Result.failure(
            IllegalStateException("Native library not loaded")
        )
        if (!nativeIsModelLoaded()) return Result.failure(
            IllegalStateException("No model loaded. Call loadModel() first.")
        )

        return try {
            val callback = object : GenerationCallback {
                override fun onToken(token: String): Boolean = onToken(token)
            }
            val result = nativeGenerate(
                prompt, maxTokens, temperature, topP, topK,
                repeatPenalty, stopSequences.toTypedArray(), callback,
            )
            Result.success(result)
        } catch (e: Exception) {
            Log.e(TAG, "Generation error", e)
            Result.failure(e)
        }
    }

    fun getModelInfo(): String? {
        if (!isLoaded || !nativeIsModelLoaded()) return null
        return try { nativeGetModelInfo() } catch (e: Exception) { null }
    }

    fun getContextLength(): Int {
        if (!isLoaded || !nativeIsModelLoaded()) return 0
        return try { nativeGetContextLength() } catch (e: Exception) { 0 }
    }

    fun tokenCount(text: String): Int {
        if (!isLoaded || !nativeIsModelLoaded()) return text.length / 4 // rough estimate
        return try { nativeTokenCount(text) } catch (e: Exception) { text.length / 4 }
    }

    fun getAvailableMemoryMb(): Long {
        return try {
            if (isLoaded) nativeGetAvailableMemory() / (1024 * 1024)
            else Runtime.getRuntime().let { (it.maxMemory() - it.totalMemory() + it.freeMemory()) / (1024 * 1024) }
        } catch (e: Exception) { 0L }
    }

    /** Callback interface for streaming token generation. */
    interface GenerationCallback {
        /** Called for each generated token. Return false to stop generation. */
        fun onToken(token: String): Boolean
    }
}
