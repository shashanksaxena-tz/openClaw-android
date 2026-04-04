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

        /**
         * Returns safe context length based on device total RAM.
         * Mirrors off-grid's getMaxContextForDevice() — prevents OOM on low-RAM devices.
         */
        fun getMaxContextForDevice(totalMemoryMb: Long, availableMemMb: Long): Int {
            val totalGb = totalMemoryMb / 1024.0
            val availGb = availableMemMb / 1024.0
            // Base tier from total RAM (matches off-grid's getMaxContextForDevice)
            val baseCap = when {
                totalGb <= 4 -> 1024
                totalGb <= 6 -> 2048
                totalGb <= 8 -> 4096
                totalGb <= 12 -> 8192
                else -> 16384
            }
            // Downgrade if available memory is tight (< 1.5GB free)
            return if (availGb < 1.5 && baseCap > 2048) baseCap / 2 else baseCap
        }

        /**
         * Returns safe GPU layer count based on device RAM.
         * Matches off-grid's ANDROID_GPU_LAYER_CAPS tiering:
         * ≤6GB → 0 (CPU-only, GPU alloc can SIGABRT)
         * ≤8GB → 12 layers
         * >8GB → 24 layers
         */
        fun getGpuLayersForDevice(totalMemoryMb: Long): Int {
            val totalGb = totalMemoryMb / 1024.0
            return when {
                totalGb <= 6 -> 0
                totalGb <= 8 -> 12
                else -> 24
            }
        }

        /** Quant formats where disabling mmap allows llama.cpp to repack weights for speed. */
        private val REPACKABLE_QUANTS = listOf("q4_0", "iq4_nl")

        /** For repackable quants on Android, disable mmap so weights get repacked at load time. */
        fun shouldUseMmap(modelPath: String): Boolean {
            val lower = modelPath.lowercase()
            return !REPACKABLE_QUANTS.any { lower.contains(it) }
        }

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

        /** Detect if a model uses Gemma-style chat template based on filename. */
        private fun isGemmaModel(modelPath: String?): Boolean {
            val lower = modelPath?.lowercase() ?: return false
            return lower.contains("gemma")
        }

        /** Build a chat-style prompt from ChatRequest for llama.cpp. */
        fun buildPrompt(request: ChatRequest, modelPath: String? = null): String {
            return if (isGemmaModel(modelPath)) {
                buildGemmaPrompt(request)
            } else {
                buildChatMlPrompt(request)
            }
        }

        /** Gemma models use <start_of_turn> / <end_of_turn> tags. */
        private fun buildGemmaPrompt(request: ChatRequest): String = buildString {
            // System prompt as a user turn (Gemma has no native system role)
            val sysPrompt = (request.systemPrompt ?: "") + LOCAL_MODEL_INSTRUCTIONS
            append("<start_of_turn>user\n")
            append("[System instructions]\n")
            append(sysPrompt.trim())

            if (!request.tools.isNullOrEmpty()) {
                append("\n\nYou have access to the following tools. To call a tool, respond with a JSON block:\n")
                append("```tool_call\n{\"name\": \"tool_name\", \"arguments\": {\"arg1\": \"value1\"}}\n```\n\n")
                append("Available tools:\n")
                for (tool in request.tools) {
                    append("- ${tool.name}: ${tool.description}\n")
                    append("  Parameters: ${tool.parameters}\n\n")
                }
            }
            append("\n<end_of_turn>\n")
            append("<start_of_turn>model\nUnderstood. I'll follow these instructions.\n<end_of_turn>\n")

            for (msg in request.messages) {
                when (msg.role) {
                    "user" -> {
                        append("<start_of_turn>user\n")
                        val text = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
                        append(text)
                        if (msg.content.any { it.type == "image_base64" }) {
                            append("\n[An image was attached but you cannot see images. Consider escalating.]")
                        }
                        append("\n<end_of_turn>\n")
                    }
                    "assistant" -> {
                        append("<start_of_turn>model\n")
                        val text = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
                        append(text)
                        msg.toolCalls?.forEach { tc ->
                            append("\n```tool_call\n")
                            append("""{"name": "${tc.name}", "arguments": ${tc.arguments}}""")
                            append("\n```")
                        }
                        append("\n<end_of_turn>\n")
                    }
                    "tool" -> {
                        append("<start_of_turn>user\n")
                        append("Tool result for ${msg.toolCallId}:\n")
                        append(msg.content.firstOrNull()?.text ?: "")
                        append("\n<end_of_turn>\n")
                    }
                }
            }
            append("<start_of_turn>model\n")
        }

        /** ChatML format (Qwen, Phi, Llama, etc.) */
        private fun buildChatMlPrompt(request: ChatRequest): String = buildString {
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

            // Get the model's actual context size to prevent prompt overflow
            val contextSize = LlamaBridge.getContextLength().let { if (it > 0) it else 4096 }

            // Resolve model path for chat template selection
            val activeId = getActiveModelId()
            val modelPath = if (activeId != null) downloadManager.getModelPath(activeId)
                else downloadManager.getDownloadedModels().firstOrNull()?.filePath
            val isGemma = isGemmaModel(modelPath)

            // Build prompt, progressively stripping content if it exceeds context
            var prompt = buildPrompt(request, modelPath)
            var promptTokens = LlamaBridge.tokenCount(prompt)

            // Reserve at least 256 tokens for generation
            val maxPromptTokens = contextSize - 256

            if (promptTokens > maxPromptTokens) {
                // First: strip tool definitions (a 1.5B model barely uses them anyway)
                Log.w(TAG, "Prompt too large ($promptTokens tokens > $maxPromptTokens max). Stripping tools.")
                val strippedRequest = request.copy(tools = null)
                prompt = buildPrompt(strippedRequest, modelPath)
                promptTokens = LlamaBridge.tokenCount(prompt)
            }

            if (promptTokens > maxPromptTokens) {
                // Second: use a minimal system prompt
                Log.w(TAG, "Still too large ($promptTokens tokens). Using minimal system prompt.")
                val minimalRequest = request.copy(
                    tools = null,
                    systemPrompt = "You are a helpful AI assistant running locally on an Android phone. Be concise.",
                )
                prompt = buildPrompt(minimalRequest, modelPath)
                promptTokens = LlamaBridge.tokenCount(prompt)
            }

            if (promptTokens > maxPromptTokens) {
                // Third: truncate old messages — keep only last 2
                Log.w(TAG, "Still too large ($promptTokens tokens). Truncating conversation.")
                val recentMessages = request.messages.takeLast(2)
                val truncatedRequest = ChatRequest(
                    model = request.model,
                    messages = recentMessages,
                    tools = null,
                    systemPrompt = "You are a helpful AI assistant. Be concise.",
                    maxTokens = request.maxTokens,
                    temperature = request.temperature,
                )
                prompt = buildPrompt(truncatedRequest, modelPath)
                promptTokens = LlamaBridge.tokenCount(prompt)
            }

            if (promptTokens > maxPromptTokens) {
                // Give up — prompt is still too large even after all truncation
                onError(IllegalStateException(
                    "Message is too long for the local model (${promptTokens} tokens, max ${maxPromptTokens}). " +
                    "Try a shorter message or switch to a cloud model."
                ))
                return@withContext
            }

            // Cap generation tokens to what's left in the context
            val maxGenTokens = minOf(request.maxTokens, 2048, contextSize - promptTokens)
            Log.i(TAG, "Prompt: $promptTokens tokens, generating up to $maxGenTokens tokens (context: $contextSize)")

            val fullText = StringBuilder()
            var cancelled = false

            val result = try {
                LlamaBridge.generate(
                    prompt = prompt,
                    maxTokens = maxGenTokens,
                    temperature = request.temperature.toFloat(),
                    stopSequences = if (isGemma) listOf("<end_of_turn>", "<start_of_turn>")
                        else listOf("<|im_end|>", "<|im_start|>"),
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

            // Estimate token usage (promptTokens already calculated above)
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

        // Validate GGUF magic number to catch corrupted/incomplete downloads
        // before native code crashes trying to parse them
        val modelFile = java.io.File(modelPath)
        if (!modelFile.exists() || modelFile.length() < 8) {
            return "Model file is missing or empty. Try re-downloading the model in Settings."
        }
        try {
            val magic = java.io.RandomAccessFile(modelFile, "r").use { raf ->
                val bytes = ByteArray(4)
                raf.read(bytes)
                String(bytes)
            }
            if (magic != "GGUF") {
                Log.e(TAG, "Invalid GGUF magic: '$magic' in $modelPath")
                return "Model file appears corrupted (invalid format). Delete it in Settings and re-download."
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot read model file header", e)
            return "Cannot read model file. It may be corrupted — try re-downloading."
        }

        // Memory budget: off-grid uses modelSize×1.5 and caps at 60% of device RAM.
        // With mmap the model itself is NOT in RAM — we need memory for KV cache,
        // scratch buffers, and some headroom. Minimum 300MB to even attempt loading.
        val availableMemMb = LlamaBridge.getAvailableMemoryMb()
        val totalMemMb = LlamaBridge.getTotalMemoryMb()
        val modelSizeMb = modelFile.length() / (1024 * 1024)
        Log.i(TAG, "Available memory: ${availableMemMb}MB, total: ${totalMemMb}MB, model size: ${modelSizeMb}MB, path: $modelPath")

        val minRequiredMb = 300L // Absolute floor — need at least this for KV cache + buffers
        if (availableMemMb < minRequiredMb) {
            Log.e(TAG, "Critically low RAM: ${availableMemMb}MB available (need at least ${minRequiredMb}MB)")
            return "Not enough free memory (${availableMemMb}MB available, need ${minRequiredMb}MB). " +
                "Close other apps and try again, or download a smaller model."
        }

        // Device-aware configuration — aligned with off-grid's architecture
        val cpuCores = Runtime.getRuntime().availableProcessors()
        val nThreads = minOf(maxOf(1, cpuCores - 2), 6) // Up to 6 threads (off-grid uses 6 on Android)
        val contextSize = getMaxContextForDevice(totalMemMb, availableMemMb)
        // GPU offloading: enable on 8GB+ devices (off-grid uses 12-24 layers)
        val nGpuLayers = getGpuLayersForDevice(totalMemMb)
        val useMmap = shouldUseMmap(modelPath)

        // Flash attention: enable on 8GB+ CPU-only devices (reduces KV cache memory).
        // Off-grid makes this toggleable; we auto-enable when safe.
        // Disabled when GPU layers > 0 on Android — OpenCL backend SIGSEGVs with flash attn.
        val useFlashAttn = (totalMemMb / 1024.0) >= 8 && nGpuLayers == 0

        Log.i(TAG, "Device config: totalRAM=${totalMemMb}MB, ctx=$contextSize, gpu_layers=$nGpuLayers, mmap=$useMmap, flash_attn=$useFlashAttn, threads=$nThreads")

        val result = try {
            LlamaBridge.loadModel(
                modelPath = modelPath,
                nThreads = nThreads,
                nGpuLayers = nGpuLayers,
                contextSize = contextSize,
                useMmap = useMmap,
                flashAttn = useFlashAttn,
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
