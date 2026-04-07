package com.openclaw.android.llm

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.UUID

/**
 * Local on-device LLM provider using Google's LiteRT-LM SDK.
 *
 * Replaces the old LlamaProvider (llama.cpp via JNI) with a clean, production-grade
 * implementation. Key improvements:
 *
 *  - No manual prompt template building — LiteRT-LM handles chat formatting
 *  - No JNI/C++ — pure Kotlin via Maven dependency
 *  - No manual context window management — engine handles KV-cache
 *  - No GPU/thread/mmap tuning — engine auto-optimizes for device
 *  - Supports GPU acceleration out of the box (no Adreno hangs)
 *  - Works with Gemma 1B (557MB) even on 4GB RAM devices
 *
 * The provider still supports:
 *  - Tool calling via markdown code blocks (parsed from model output)
 *  - Escalation to cloud when the local model can't handle a task
 *  - Streaming token output
 */
class LiteRTProvider(
    private val downloadManager: ModelDownloadManager,
    private val getActiveModelId: () -> String?,
    private val appContext: android.content.Context? = null,
) : LlmProvider {

    companion object {
        private const val TAG = "LiteRTProvider"
        const val ESCALATION_MARKER = "[ESCALATE_TO_CLOUD]"

        private val TOOL_CALL_PATTERN = Regex(
            "```tool_call\\s*\\n(\\{[^`]+\\})\\s*\\n```",
            RegexOption.DOT_MATCHES_ALL,
        )
    }

    // ── LlmProvider interface ───────────────────────────────────────────────

    override val providerId = "local-llama"  // Keep same ID for compatibility with ModelRouter
    override val displayName = "On-Device (LiteRT-LM)"
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
        if (!LiteRTBridge.isAvailable) return false
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
            // ── Step 1: Ensure model is loaded ──────────────────────────────
            val loadError = ensureModelLoaded()
            if (loadError != null) {
                Log.e(TAG, loadError)
                onError(IllegalStateException(loadError))
                return@withContext
            }

            // ── Step 2: Create/reset conversation with system prompt ────────
            val systemPrompt = request.systemPrompt ?: ""
            val toolInstructions = buildToolInstructions(request.tools)
            val fullSystemPrompt = if (toolInstructions.isNotEmpty()) {
                "$systemPrompt\n\n$toolInstructions"
            } else {
                systemPrompt
            }

            val convResult = LiteRTBridge.createConversation(
                systemInstruction = fullSystemPrompt.ifBlank { null },
                temperature = request.settings.temperature.toFloat(),
            )
            if (convResult.isFailure) {
                val msg = "Failed to create conversation: ${convResult.exceptionOrNull()?.message}"
                Log.e(TAG, msg)
                onError(IllegalStateException(msg))
                return@withContext
            }

            // ── Step 3: Build the user message ──────────────────────────────
            val userMessage = buildUserMessage(request)
            if (userMessage.isBlank()) {
                onError(IllegalStateException("Empty message"))
                return@withContext
            }

            val startMs = System.currentTimeMillis()
            var tokenCount = 0

            // ── Step 4: Send message and stream response ────────────────────
            // Uses MessageCallback pattern (matching Edge Gallery exactly)
            LiteRTBridge.sendMessage(
                message = userMessage,
                onToken = { token ->
                    tokenCount++
                    onChunk(token)
                    true // keep generating
                },
                onDone = { fullResponse ->
                    val elapsed = System.currentTimeMillis() - startMs
                    val tokensPerSec = if (elapsed > 0) tokenCount * 1000.0 / elapsed else 0.0
                    Log.i(TAG, "Generated $tokenCount tokens in ${elapsed}ms (${String.format("%.1f", tokensPerSec)} t/s)")

                    val toolCalls = parseToolCalls(fullResponse)
                    val promptTokens = LiteRTBridge.estimateTokenCount(userMessage)

                    onDone(ChatResponse(
                        content = cleanResponse(fullResponse),
                        toolCalls = toolCalls,
                        usage = TokenUsage(promptTokens, tokenCount, promptTokens + tokenCount),
                        finishReason = when {
                            fullResponse.contains(ESCALATION_MARKER) -> "escalate"
                            toolCalls.isNotEmpty() -> "tool_calls"
                            else -> "stop"
                        },
                    ))
                },
                onError = { e ->
                    Log.e(TAG, "Inference error", e)
                    onError(e)
                },
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Local inference error", e)
            val ex = if (e is Exception) e else RuntimeException("Inference error: ${e.message}", e)
            onError(ex)
        }
    }

    fun unloadModel() = LiteRTBridge.unloadModel()

    fun getLoadedModelInfo(): String? = LiteRTBridge.getLoadedModelPath()?.let { path ->
        val file = java.io.File(path)
        "LiteRT-LM: ${file.name} (${file.length() / (1024 * 1024)}MB)"
    }

    /**
     * Proactively load the model in the background.
     *
     * Edge Gallery loads the model when you open a chat (takes ~60s on first load).
     * This method should be called early (e.g. at app startup or when chat opens)
     * so the model is ready when the user sends their first message.
     */
    suspend fun preloadModel() = withContext(Dispatchers.IO) {
        if (LiteRTBridge.isModelLoaded) return@withContext
        val error = ensureModelLoaded()
        if (error != null) {
            Log.w(TAG, "Preload failed: $error")
        } else {
            Log.i(TAG, "Model preloaded successfully")
        }
    }

    // ── Private: model loading ──────────────────────────────────────────────

    private fun resolveModelPath(): String? {
        val activeId = getActiveModelId()
        return if (activeId != null) downloadManager.getModelPath(activeId)
        else downloadManager.getDownloadedModels().firstOrNull()?.filePath
    }

    /**
     * Ensure a model is loaded. Returns null if OK, or an error message.
     *
     * LiteRT-LM handles all the complexity that was previously manual:
     *  - No need to detect CPU cores, GPU layers, flash attention
     *  - No need for mmap configuration
     *  - No fallback loading attempts with different configs
     *  - The engine auto-optimizes for the device on first load
     */
    private fun ensureModelLoaded(): String? {
        // Already loaded?
        val currentPath = LiteRTBridge.getLoadedModelPath()
        val targetPath = resolveModelPath()

        if (currentPath != null && currentPath == targetPath) {
            return null // Already loaded with the right model
        }

        if (targetPath == null) {
            return "No model downloaded. Go to Settings to download one."
        }

        val modelFile = java.io.File(targetPath)
        if (!modelFile.exists() || modelFile.length() < 100) {
            return "Model file missing or corrupted. Re-download in Settings."
        }

        // Check RAM — LiteRT-LM models are much more efficient than GGUF,
        // but we still need a baseline.
        val availMb = LiteRTBridge.getAvailableMemoryMb(appContext)
        if (availMb < 150) {
            return "Not enough RAM (${availMb}MB free). Close other apps and try again."
        }

        // Load the model with CPU backend (safe on all devices).
        // GPU can cause native crashes (SIGSEGV) on some devices that bypass
        // Java exception handling and kill the process instantly.
        // Edge Gallery also defaults to CPU and lets users opt-in to GPU.
        Log.i(TAG, "Loading model: $targetPath (${modelFile.length() / (1024 * 1024)}MB, avail RAM: ${availMb}MB)")
        val start = System.currentTimeMillis()

        val result = LiteRTBridge.loadModel(targetPath, Backend.CPU())

        val elapsed = System.currentTimeMillis() - start
        return if (result.isSuccess) {
            Log.i(TAG, "Model loaded in ${elapsed}ms")
            null
        } else {
            val msg = result.exceptionOrNull()?.message ?: "Unknown error"
            Log.e(TAG, "Failed to load model after ${elapsed}ms: $msg")
            "Failed to load model: $msg. Try a smaller model or restart the app."
        }
    }

    // ── Private: message building ───────────────────────────────────────────

    /**
     * Build the user message to send to the LiteRT-LM conversation.
     *
     * Unlike the old llama.cpp approach where we had to manually build the
     * entire prompt with chat templates, LiteRT-LM's Conversation API handles
     * the chat format. We just need to provide the content.
     *
     * For multi-turn, we include recent history in the first message of each
     * new conversation session.
     */
    private fun buildUserMessage(request: ChatRequest): String {
        val messages = request.messages
        if (messages.isEmpty()) return ""

        // If there's only one message, just send it
        if (messages.size <= 2) {
            return messages.last().let { msg ->
                msg.content.firstOrNull { it.type == "text" }?.text ?: ""
            }
        }

        // For multi-turn: include recent history as context in the message
        // (since we create a fresh conversation each time for simplicity)
        val sb = StringBuilder()

        // Include up to the last 4 messages as context (excluding the current one)
        val historyMessages = messages.dropLast(1).takeLast(4)
        if (historyMessages.isNotEmpty()) {
            sb.append("[Recent conversation context]\n")
            for (msg in historyMessages) {
                val role = when (msg.role) {
                    "user" -> "User"
                    "assistant" -> "Assistant"
                    "tool" -> "Tool result"
                    else -> msg.role
                }
                val text = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
                if (text.isNotBlank()) {
                    // Truncate long history entries
                    val truncated = if (text.length > 500) text.take(500) + "..." else text
                    sb.append("$role: $truncated\n")
                }
            }
            sb.append("\n[Current message]\n")
        }

        // Add the actual current message
        val currentMsg = messages.last()
        val currentText = currentMsg.content.firstOrNull { it.type == "text" }?.text ?: ""
        sb.append(currentText)

        // Note if images were attached (can't process them locally)
        if (currentMsg.content.any { it.type == "image_base64" }) {
            sb.append("\n[Note: An image was attached but you cannot see images. Consider escalating to cloud.]")
        }

        return sb.toString()
    }

    /**
     * Build tool instructions to include in the system prompt.
     */
    private fun buildToolInstructions(tools: List<ToolDefinition>?): String {
        if (tools.isNullOrEmpty()) return ""
        return buildString {
            append("To call a tool, respond with:\n```tool_call\n{\"name\": \"tool_name\", \"arguments\": {...}}\n```\n\nTools:\n")
            for (tool in tools) {
                append("- ${tool.name}: ${tool.description}\n")
            }
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
