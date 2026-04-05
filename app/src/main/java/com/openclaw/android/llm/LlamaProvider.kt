package com.openclaw.android.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local on-device LLM provider using llama.cpp via JNI.
 *
 * Architecture:
 * - [DeviceProfile] handles all hardware detection and capability tiering
 * - [ChatTemplate] handles prompt formatting per model family
 * - [InferenceLog] captures diagnostics for debugging
 * - This class orchestrates: load → prompt → generate → parse
 */
class LlamaProvider(
    private val downloadManager: ModelDownloadManager,
    private val getActiveModelId: () -> String?,
    private val appContext: android.content.Context? = null,
) : LlmProvider {

    companion object {
        private const val TAG = "LlamaProvider"
        const val ESCALATION_MARKER = "[ESCALATE_TO_CLOUD]"
        private const val GENERATE_TIMEOUT_MS = 90_000L // 90s hard limit

        private val TOOL_CALL_PATTERN = Regex(
            "```tool_call\\s*\\n(\\{[^`]+\\})\\s*\\n```",
            RegexOption.DOT_MATCHES_ALL,
        )

        private val REPACKABLE_QUANTS = listOf("q4_0", "iq4_nl")

        fun shouldUseMmap(modelPath: String): Boolean =
            !REPACKABLE_QUANTS.any { modelPath.lowercase().contains(it) }
    }

    // ── LlmProvider interface ───────────────────────────────────────────────

    override val providerId = "local-llama"
    override val displayName = "On-Device (Local)"
    override val supportsVision = false
    override val supportsToolUse = true

    override val availableModels: List<ModelInfo>
        get() = downloadManager.getDownloadedModels().map { dl ->
            ModelInfo(
                id = "local:${dl.model.id}",
                displayName = "${dl.model.name} (On-Device)",
                contextWindow = dl.model.contextWindow,
                supportsVision = false,
                supportsToolUse = dl.model.supportsToolUse,
            )
        }

    override fun isConfigured(): Boolean {
        if (!LlamaBridge.isLoaded || !LlamaBridge.isRealBuild) return false
        val activeId = getActiveModelId()
        return if (activeId != null) downloadManager.isModelDownloaded(activeId)
        else downloadManager.getDownloadedModels().isNotEmpty()
    }

    override suspend fun chatCompletion(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            val loadError = loadModelIfNeeded()
            if (loadError != null) {
                InferenceLog.logError(TAG, loadError)
                onError(IllegalStateException(loadError))
                return@withContext
            }

            val modelPath = resolveModelPath()
            val contextSize = LlamaBridge.getContextLength().let { if (it > 0) it else 2048 }
            val prompt = buildAndFitPrompt(request, modelPath, contextSize)
            if (prompt == null) {
                val msg = "Message too long for local model. Try a shorter message or switch to cloud."
                InferenceLog.logError(TAG, msg)
                onError(IllegalStateException(msg))
                return@withContext
            }

            val promptTokens = LlamaBridge.tokenCount(prompt)
            val maxGenTokens = minOf(request.maxTokens, 2048, contextSize - promptTokens)
            InferenceLog.logInferenceStart(promptTokens, maxGenTokens, contextSize)

            val result = generate(prompt, maxGenTokens, request.temperature.toFloat(), modelPath, onChunk)

            when (result) {
                is GenerateResult.Success -> {
                    val toolCalls = parseToolCalls(result.text)
                    val completionTokens = LlamaBridge.tokenCount(result.text)
                    InferenceLog.logInferenceEnd(
                        completionTokens, result.durationMs,
                        if (result.durationMs > 0) completionTokens * 1000.0 / result.durationMs else 0.0,
                    )
                    onDone(ChatResponse(
                        content = cleanResponse(result.text),
                        toolCalls = toolCalls,
                        usage = TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens),
                        finishReason = when {
                            result.text.contains(ESCALATION_MARKER) -> "escalate"
                            toolCalls.isNotEmpty() -> "tool_calls"
                            else -> "stop"
                        },
                    ))
                }
                is GenerateResult.Timeout -> {
                    val msg = "Local model too slow (no response in ${GENERATE_TIMEOUT_MS / 1000}s). Try a smaller model."
                    InferenceLog.logError(TAG, msg)
                    onError(IllegalStateException(msg))
                }
                is GenerateResult.Error -> {
                    InferenceLog.logError(TAG, result.message)
                    onError(RuntimeException(result.message))
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Local inference error", e)
            InferenceLog.logError(TAG, e.message ?: "unknown")
            val ex = if (e is Exception) e else RuntimeException("Native crash: ${e.message}", e)
            onError(ex)
        }
    }

    fun unloadModel() = LlamaBridge.unloadModel()
    fun getLoadedModelInfo(): String? = LlamaBridge.getModelInfo()

    // ── Private: model loading ──────────────────────────────────────────────

    private fun resolveModelPath(): String? {
        val activeId = getActiveModelId()
        return if (activeId != null) downloadManager.getModelPath(activeId)
        else downloadManager.getDownloadedModels().firstOrNull()?.filePath
    }

    private fun loadModelIfNeeded(): String? {
        if (LlamaBridge.isModelLoaded()) return null
        if (!LlamaBridge.isLoaded) return "Native library not loaded."
        if (!LlamaBridge.isRealBuild) return "llama.cpp not compiled in this build. Switch to a cloud model."

        val modelPath = resolveModelPath() ?: return "No model downloaded. Go to Settings to download one."
        val modelFile = java.io.File(modelPath)

        if (!modelFile.exists() || modelFile.length() < 8) return "Model file missing or empty. Re-download in Settings."
        val magic = try {
            java.io.RandomAccessFile(modelFile, "r").use { raf ->
                val bytes = ByteArray(4); raf.read(bytes); String(bytes)
            }
        } catch (_: Exception) { "" }
        if (magic != "GGUF") return "Model file corrupted. Delete and re-download."

        val device = DeviceProfile.detect(appContext)
        if (!device.canLoadModel) {
            return "Not enough RAM (${device.availableMemMb}MB free, need ${DeviceProfile.MIN_RAM_MB}MB). Close other apps."
        }

        val useMmap = shouldUseMmap(modelPath)
        InferenceLog.logModelLoad(modelPath, mapOf(
            "totalRAM" to "${device.totalMemMb}MB", "freeRAM" to "${device.availableMemMb}MB",
            "ctx" to device.maxContext, "gpu" to device.gpuLayers,
            "threads" to device.threads, "flash" to device.flashAttention, "mmap" to useMmap,
        ))

        data class Attempt(val gpu: Int, val ctx: Int, val flash: Boolean, val label: String)
        val attempts = buildList {
            add(Attempt(device.gpuLayers, device.maxContext, device.flashAttention, "gpu=${device.gpuLayers} ctx=${device.maxContext}"))
            if (device.gpuLayers > 0) add(Attempt(0, device.maxContext, device.flashAttention, "cpu ctx=${device.maxContext}"))
            if (device.maxContext > 2048) add(Attempt(0, 2048, false, "cpu ctx=2048"))
        }

        for ((i, a) in attempts.withIndex()) {
            val start = System.currentTimeMillis()
            val ok = try {
                LlamaBridge.loadModel(modelPath, device.threads, a.gpu, a.ctx, useMmap, a.flash).isSuccess
            } catch (e: Throwable) {
                Log.e(TAG, "Load crashed: ${e.message}"); false
            }
            val elapsed = System.currentTimeMillis() - start
            InferenceLog.logLoadAttempt(i + 1, attempts.size, a.label, ok, elapsed)
            if (ok) return null
        }

        return "Failed to load model after ${attempts.size} attempts. Try a smaller model."
    }

    // ── Private: prompt building ────────────────────────────────────────────

    private fun buildAndFitPrompt(request: ChatRequest, modelPath: String?, contextSize: Int): String? {
        val maxTokens = contextSize - 256
        val template = if (ChatTemplate.isGemma(modelPath)) "gemma" else "chatml"

        var prompt = ChatTemplate.build(request, modelPath)
        var tokens = LlamaBridge.tokenCount(prompt)
        if (tokens <= maxTokens) {
            InferenceLog.logPrompt(tokens, maxTokens, "none", template)
            return prompt
        }

        prompt = ChatTemplate.build(request.copy(tools = null), modelPath)
        tokens = LlamaBridge.tokenCount(prompt)
        if (tokens <= maxTokens) {
            InferenceLog.logPrompt(tokens, maxTokens, "tools", template)
            return prompt
        }

        prompt = ChatTemplate.build(request.copy(
            tools = null, systemPrompt = "You are a helpful AI on an Android phone. Be concise.",
        ), modelPath)
        tokens = LlamaBridge.tokenCount(prompt)
        if (tokens <= maxTokens) {
            InferenceLog.logPrompt(tokens, maxTokens, "tools+sysprompt", template)
            return prompt
        }

        prompt = ChatTemplate.build(ChatRequest(
            model = request.model, messages = request.messages.takeLast(2),
            systemPrompt = "Be concise.", maxTokens = request.maxTokens, temperature = request.temperature,
        ), modelPath)
        tokens = LlamaBridge.tokenCount(prompt)
        if (tokens <= maxTokens) {
            InferenceLog.logPrompt(tokens, maxTokens, "tools+sysprompt+history", template)
            return prompt
        }

        InferenceLog.logError(TAG, "Prompt still $tokens tokens after all stripping (max $maxTokens)")
        return null
    }

    // ── Private: generation ─────────────────────────────────────────────────

    private sealed class GenerateResult {
        data class Success(val text: String, val durationMs: Long) : GenerateResult()
        data class Timeout(val afterMs: Long) : GenerateResult()
        data class Error(val message: String) : GenerateResult()
    }

    /** Container for generate result to avoid Kotlin Result + Java interop issues. */
    private class NativeResult(val text: String?, val error: Throwable?)

    /**
     * Run native generate with a hard timeout.
     * Uses a separate thread so that if llama_decode() hangs during prefill,
     * we can timeout and return an error instead of blocking forever.
     */
    private fun generate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        modelPath: String?,
        onChunk: (String) -> Unit,
    ): GenerateResult {
        val startMs = System.currentTimeMillis()
        var gotFirstToken = false
        // Use array for thread-safe mutable flag (can't use @Volatile on local vars)
        val cancelFlag = booleanArrayOf(false)
        val fullText = StringBuilder()

        InferenceLog.log(TAG, "Calling native generate (${prompt.length} chars, maxTokens=$maxTokens)...")

        val executor = Executors.newSingleThreadExecutor()
        val callable = Callable<NativeResult> {
            try {
                val result = LlamaBridge.generate(
                    prompt = prompt,
                    maxTokens = maxTokens,
                    temperature = temperature,
                    stopSequences = ChatTemplate.stopSequences(modelPath),
                    onToken = fun(token: String): Boolean {
                        if (cancelFlag[0]) return false
                        if (!gotFirstToken) {
                            gotFirstToken = true
                            InferenceLog.logFirstToken(System.currentTimeMillis() - startMs)
                        }
                        fullText.append(token)
                        try { onChunk(token) } catch (_: Exception) {}
                        if (fullText.contains(ESCALATION_MARKER)) {
                            cancelFlag[0] = true
                            return false
                        }
                        return true
                    },
                )
                NativeResult(result.getOrNull(), result.exceptionOrNull())
            } catch (e: Throwable) {
                NativeResult(null, e)
            }
        }

        val future = executor.submit(callable)
        val nativeResult: NativeResult
        try {
            nativeResult = future.get(GENERATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: java.util.concurrent.TimeoutException) {
            cancelFlag[0] = true
            InferenceLog.logError(TAG, "HARD TIMEOUT after ${GENERATE_TIMEOUT_MS / 1000}s — native generate blocked (check logcat for step details)")
            executor.shutdownNow()
            return GenerateResult.Timeout(System.currentTimeMillis() - startMs)
        } catch (e: Exception) {
            executor.shutdownNow()
            return GenerateResult.Error("Generate thread error: ${e.cause?.message ?: e.message}")
        } finally {
            executor.shutdown()
        }

        val elapsed = System.currentTimeMillis() - startMs
        return if (nativeResult.error != null) {
            GenerateResult.Error(nativeResult.error.message ?: "Generation failed")
        } else {
            GenerateResult.Success(nativeResult.text ?: fullText.toString(), elapsed)
        }
    }

    // ── Private: response parsing ───────────────────────────────────────────

    private fun parseToolCalls(text: String): List<ToolCallRequest> =
        TOOL_CALL_PATTERN.findAll(text).mapNotNull { match ->
            try {
                val json = Json.parseToJsonElement(match.groupValues[1].trim()).jsonObject
                ToolCallRequest(
                    id = UUID.randomUUID().toString(),
                    name = json["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    arguments = json["arguments"] ?: JsonObject(emptyMap()),
                )
            } catch (_: Exception) { null }
        }.toList()

    private fun cleanResponse(text: String): String {
        var cleaned = text.replace(Regex("```tool_call\\s*\\n\\{[^`]+\\}\\s*\\n```"), "").trim()
        if (cleaned.contains(ESCALATION_MARKER)) {
            val reason = cleaned.substringAfter(ESCALATION_MARKER).trim()
            cleaned = "$ESCALATION_MARKER ${reason.ifBlank { "Task requires cloud model." }}"
        }
        return cleaned
    }
}
