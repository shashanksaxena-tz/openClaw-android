package com.openclaw.android.llm

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Local on-device LLM provider using llama.cpp via JNI.
 *
 * Key design: The local model is the **first responder**. It receives every request
 * and self-determines whether it can handle it. If not, it responds with an
 * [ESCALATE_TO_CLOUD] marker, which AgentRuntime detects and re-routes to a cloud provider.
 *
 * This means the intelligence of "what's too complex" lives in the model itself,
 * not in hardcoded heuristics.
 */
class LlamaProvider(
    private val downloadManager: ModelDownloadManager,
    private val getActiveModelId: () -> String?,
) : LlmProvider {

    companion object {
        private const val TAG = "LlamaProvider"

        /**
         * Marker the local model emits when it determines it cannot handle the request.
         * AgentRuntime watches for this and re-routes to cloud.
         */
        const val ESCALATION_MARKER = "[ESCALATE_TO_CLOUD]"

        /** Regex for parsing tool_call blocks from model output. */
        private val TOOL_CALL_PATTERN = Regex("```tool_call\\s*\\n(\\{[^`]+\\})\\s*\\n```", RegexOption.DOT_MATCHES_ALL)

        /** System prompt addendum that teaches the model about escalation. */
        private const val LOCAL_MODEL_INSTRUCTIONS = """

## On-device model instructions

You are running LOCALLY on the user's phone. You have LIMITED capabilities compared to cloud models.

IMPORTANT: If you determine that a request is beyond your ability, respond with EXACTLY:
[ESCALATE_TO_CLOUD] <brief reason>

Escalate when:
- The task requires analyzing images (you cannot see images)
- The task requires very long, complex multi-step reasoning across many topics
- The task involves writing long-form content (essays, full documents)
- You are unsure about factual accuracy for critical information
- The task requires 4+ chained tool calls that depend on each other
- The user explicitly asks for a "better" or "cloud" or "smarter" answer

Do NOT escalate for:
- Simple questions, greetings, casual conversation
- Single tool calls (calendar, contacts, tasks, notes, reminders)
- Short text responses (summaries, lists, quick answers)
- Basic tool orchestration (1-3 independent tool calls)
- Following up on previous conversation context

When you CAN handle the task, respond normally. Be concise — you're on a phone.
"""

        /** Build a chat-style prompt from ChatRequest for llama.cpp. */
        fun buildPrompt(request: ChatRequest): String = buildString {
            // System prompt
            val sysPrompt = (request.systemPrompt ?: "") + LOCAL_MODEL_INSTRUCTIONS
            append("<|im_start|>system\n")
            append(sysPrompt.trim())
            append("\n<|im_end|>\n")

            // Tool definitions (if model supports tool use)
            if (!request.tools.isNullOrEmpty()) {
                append("<|im_start|>system\n")
                append("You have access to the following tools. To call a tool, respond with a JSON block:\n")
                append("```tool_call\n{\"name\": \"tool_name\", \"arguments\": {\"arg1\": \"value1\"}}\n```\n\n")
                append("Available tools:\n")
                for (tool in request.tools) {
                    append("- ${tool.name}: ${tool.description}\n")
                    append("  Parameters: ${tool.parameters}\n\n")
                }
                append("<|im_end|>\n")
            }

            // Chat messages
            for (msg in request.messages) {
                when (msg.role) {
                    "user" -> {
                        append("<|im_start|>user\n")
                        val text = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
                        append(text)
                        // Note if images are present (model should escalate)
                        if (msg.content.any { it.type == "image_base64" }) {
                            append("\n[An image was attached but you cannot see images. Consider escalating.]")
                        }
                        append("\n<|im_end|>\n")
                    }
                    "assistant" -> {
                        append("<|im_start|>assistant\n")
                        val text = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
                        append(text)
                        msg.toolCalls?.forEach { tc ->
                            append("\n```tool_call\n")
                            append("""{"name": "${tc.name}", "arguments": ${tc.arguments}}""")
                            append("\n```")
                        }
                        append("\n<|im_end|>\n")
                    }
                    "tool" -> {
                        append("<|im_start|>tool\n")
                        append("Tool result for ${msg.toolCallId}:\n")
                        append(msg.content.firstOrNull()?.text ?: "")
                        append("\n<|im_end|>\n")
                    }
                }
            }

            // Start assistant turn
            append("<|im_start|>assistant\n")
        }
    }

    override val providerId = "local-llama"
    override val displayName = "On-Device (Local)"
    override val supportsVision = false
    override val supportsToolUse = true

    override val availableModels: List<ModelInfo>
        get() = downloadManager.getDownloadedModels().map { downloaded ->
            ModelInfo(
                id = "local:${downloaded.model.id}",
                displayName = "${downloaded.model.name} (On-Device)",
                contextWindow = downloaded.model.contextWindow,
                supportsVision = false,
                supportsToolUse = downloaded.model.supportsToolUse,
            )
        }

    override fun isConfigured(): Boolean {
        // Not configured if the native library is a stub build — local inference
        // cannot work without llama.cpp compiled in.
        if (!LlamaBridge.isLoaded || !LlamaBridge.isRealBuild) return false

        val activeId = getActiveModelId()
        if (activeId != null) {
            return downloadManager.isModelDownloaded(activeId)
        }
        return downloadManager.getDownloadedModels().isNotEmpty()
    }

    override suspend fun chatCompletion(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            // Ensure model is loaded
            val loadError = ensureModelLoadedWithReason()
            if (loadError != null) {
                onError(IllegalStateException(loadError))
                return@withContext
            }

            val prompt = buildPrompt(request)
            val fullText = StringBuilder()
            var cancelled = false

            val result = try {
                LlamaBridge.generate(
                    prompt = prompt,
                    maxTokens = minOf(request.maxTokens, 2048), // Cap local model output
                    temperature = request.temperature.toFloat(),
                    stopSequences = listOf("<|im_end|>", "<|im_start|>"),
                    onToken = { token ->
                        if (!cancelled) {
                            try {
                                fullText.append(token)
                                onChunk(token)
                            } catch (e: Exception) {
                                Log.w(TAG, "Error in onToken callback", e)
                            }

                            // Early escalation detection
                            if (fullText.contains(ESCALATION_MARKER)) {
                                cancelled = true
                                return@generate false
                            }
                        }
                        !cancelled && isActive
                    },
                )
            } catch (e: Throwable) {
                // Catch Throwable (not just Exception) to handle native crashes
                // that surface as Error (e.g. UnsatisfiedLinkError, OutOfMemoryError)
                Log.e(TAG, "Native generate call crashed", e)
                Result.failure(RuntimeException("Local model inference failed: ${e.message ?: "native crash"}", e))
            }

            val responseText = result.getOrElse { e ->
                val ex = if (e is Exception) e else RuntimeException(e.message ?: "Inference failed", e)
                onError(ex)
                return@withContext
            }

            // Parse tool calls from response
            val toolCalls = parseToolCalls(responseText)

            // Estimate token usage
            val promptTokens = LlamaBridge.tokenCount(prompt)
            val completionTokens = LlamaBridge.tokenCount(responseText)

            onDone(
                ChatResponse(
                    content = cleanResponse(responseText),
                    toolCalls = toolCalls,
                    usage = TokenUsage(promptTokens, completionTokens, promptTokens + completionTokens),
                    finishReason = when {
                        responseText.contains(ESCALATION_MARKER) -> "escalate"
                        toolCalls.isNotEmpty() -> "tool_calls"
                        else -> "stop"
                    },
                )
            )
        } catch (e: Throwable) {
            // Catch Throwable to handle OutOfMemoryError and native crash wrappers
            Log.e(TAG, "Local inference error", e)
            val ex = if (e is Exception) e else RuntimeException("Local model crashed: ${e.message ?: "unknown native error"}", e)
            onError(ex)
        }
    }

    /**
     * Load the active model into memory if not already loaded.
     * Returns null on success, or an error message string on failure.
     */
    private fun ensureModelLoadedWithReason(): String? {
        if (LlamaBridge.isModelLoaded()) return null

        if (!LlamaBridge.isLoaded) {
            return "Local inference engine not available. The native library failed to load."
        }

        if (!LlamaBridge.isRealBuild) {
            return "Local inference not available in this build. llama.cpp was not compiled in. " +
                "Please use a build that includes on-device inference support, or switch to a cloud model."
        }

        val activeId = getActiveModelId()
        val modelPath = if (activeId != null) {
            downloadManager.getModelPath(activeId)
        } else {
            // Use first available downloaded model
            downloadManager.getDownloadedModels().firstOrNull()?.filePath
        }

        if (modelPath == null) {
            return "No local model loaded. Download a model in Settings."
        }

        // Check available memory before attempting load — prevent native OOM crash
        val availableMemMb = LlamaBridge.getAvailableMemoryMb()
        val modelFile = java.io.File(modelPath)
        val modelSizeMb = if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0L
        Log.i(TAG, "Available memory: ${availableMemMb}MB, model size: ${modelSizeMb}MB, path: $modelPath")

        // Model loading typically requires ~1.2x the file size in RAM (model weights + context buffers).
        // Refuse to load if we don't have enough headroom to avoid a native OOM crash.
        val requiredMemMb = (modelSizeMb * 1.3).toLong() // model + context overhead
        if (availableMemMb < requiredMemMb) {
            Log.e(TAG, "Insufficient RAM: need ~${requiredMemMb}MB, have ${availableMemMb}MB")
            return "Not enough free memory to load this model. " +
                "Need ~${requiredMemMb}MB but only ${availableMemMb}MB is available. " +
                "Close other apps and try again, or download a smaller model."
        }

        // Determine optimal thread count based on device
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val nThreads = maxOf(1, cpuCores - 2) // Leave 2 cores for UI/system

        val result = try {
            LlamaBridge.loadModel(
                modelPath = modelPath,
                nThreads = nThreads,
                nGpuLayers = 0, // CPU-only for now; GPU offload can be added later
                contextSize = 4096,
            )
        } catch (e: Throwable) {
            // Native code can throw Error (OutOfMemoryError, UnsatisfiedLinkError)
            // which crashes the process. Catch and convert to a Result.
            Log.e(TAG, "Native model load crashed", e)
            Result.failure(RuntimeException("Model loading crashed: ${e.message ?: "native error"}"))
        }

        if (result.isFailure) {
            val reason = result.exceptionOrNull()?.message ?: "Unknown error"
            Log.e(TAG, "Model load failed: $reason (available RAM: ${availableMemMb}MB)")
            return if (availableMemMb < 1024) {
                "Failed to load model: not enough RAM (${availableMemMb}MB available). " +
                    "Close other apps and try again, or use a smaller model."
            } else {
                "Failed to load model: $reason"
            }
        }

        return null
    }

    /** Extract tool calls from the model's response text. */
    private fun parseToolCalls(text: String): List<ToolCallRequest> {
        val toolCalls = mutableListOf<ToolCallRequest>()

        for (match in TOOL_CALL_PATTERN.findAll(text)) {
            try {
                val jsonStr = match.groupValues[1].trim()
                val json = Json.parseToJsonElement(jsonStr).jsonObject
                val name = json["name"]?.jsonPrimitive?.contentOrNull ?: continue
                val arguments = json["arguments"] ?: JsonObject(emptyMap())

                toolCalls.add(
                    ToolCallRequest(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        arguments = arguments,
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse tool call: ${match.value}", e)
            }
        }

        return toolCalls
    }

    /** Clean up the response text — remove tool call blocks and escalation markers. */
    private fun cleanResponse(text: String): String {
        var cleaned = text

        // Remove tool_call blocks (they're parsed separately)
        cleaned = cleaned.replace(Regex("```tool_call\\s*\\n\\{[^`]+\\}\\s*\\n```"), "").trim()

        // If escalating, extract the reason
        if (cleaned.contains(ESCALATION_MARKER)) {
            val reason = cleaned.substringAfter(ESCALATION_MARKER).trim()
            cleaned = if (reason.isNotBlank()) {
                "$ESCALATION_MARKER $reason"
            } else {
                "$ESCALATION_MARKER Task requires cloud model capabilities."
            }
        }

        return cleaned
    }

    /** Unload model from memory (e.g., when switching models or freeing RAM). */
    fun unloadModel() {
        LlamaBridge.unloadModel()
    }

    /** Get info about the currently loaded model. */
    fun getLoadedModelInfo(): String? = LlamaBridge.getModelInfo()
}
