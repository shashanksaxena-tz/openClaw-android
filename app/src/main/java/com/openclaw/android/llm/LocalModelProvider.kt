package com.openclaw.android.llm

import android.content.Context

/**
 * Local on-device model provider using Google's Gemini Nano via AI Core.
 * Falls back gracefully when the device doesn't support on-device inference.
 *
 * Runs entirely on-device — no data leaves the phone, ideal for
 * privacy-sensitive queries.
 *
 * Requirements:
 * - Pixel 8+ or other devices with Gemini Nano support
 * - Google AI Core system app installed and updated
 */
class LocalModelProvider(private val context: Context) : LlmProvider {

    override val providerId = "local"
    override val displayName = "On-Device (Gemini Nano)"
    override val supportsVision = false
    override val supportsToolUse = false

    private var deviceSupported: Boolean? = null
    private var statusMessage: String = "Checking availability..."

    override val availableModels = listOf(
        ModelInfo(
            id = "gemini-nano",
            displayName = "Gemini Nano (On-Device)",
            contextWindow = 4096,
            supportsVision = false,
            supportsToolUse = false,
        )
    )

    override fun isConfigured(): Boolean = checkAvailability()

    override suspend fun chatCompletion(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit,
    ) {
        if (!checkAvailability()) {
            val msg = "On-device AI is not available on this device. $statusMessage " +
                    "Requirements: Pixel 8+ or compatible device with Google AI Core installed. " +
                    "All processing happens on-device for maximum privacy."
            onChunk(msg)
            onDone(ChatResponse(content = msg))
            return
        }

        try {
            val result = runOnDeviceInference(request)
            onChunk(result)
            onDone(ChatResponse(content = result))
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun checkAvailability(): Boolean {
        if (deviceSupported != null) return deviceSupported!!

        return try {
            val pm = context.packageManager
            try {
                pm.getPackageInfo("com.google.android.aicore", 0)
                deviceSupported = true
                statusMessage = "Google AI Core found"
                true
            } catch (e: Exception) {
                deviceSupported = false
                statusMessage = "Google AI Core app not installed. Install it from Play Store."
                false
            }
        } catch (e: Exception) {
            deviceSupported = false
            statusMessage = "Cannot check device capability: ${e.message}"
            false
        }
    }

    private fun runOnDeviceInference(request: ChatRequest): String {
        // Build prompt from chat messages (Nano has limited context)
        val prompt = buildString {
            request.systemPrompt?.let { append("Instructions: $it\n\n") }
            request.messages.takeLast(4).forEach { msg ->
                when (msg.role) {
                    "user" -> {
                        val text = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
                        append("User: $text\n")
                    }
                    "assistant" -> {
                        val text = msg.content.firstOrNull { it.type == "text" }?.text ?: ""
                        append("Assistant: $text\n")
                    }
                }
            }
            append("Assistant: ")
        }

        // Google AI Core SDK is required as a Gradle dependency for on-device inference.
        // Without the compile-time dependency, we cannot invoke the model.
        // The availability check above confirms the AI Core app is installed,
        // but the SDK integration requires adding the aicore dependency to build.gradle:
        //   implementation("com.google.ai.edge.aicore:aicore:0.0.4-alpha01")
        //
        // For now, return a helpful message. When the SDK is added, replace this
        // with proper GenerativeModel initialization and generateContent() calls.
        return "On-device inference is available on this device (Google AI Core detected), " +
                "but the Gemini Nano SDK integration is not yet enabled in this build. " +
                "Please use a cloud model (Gemini, Groq, or Cerebras) for now. " +
                "On-device support is coming in a future update."
    }

    fun getStatus(): String = buildString {
        append("Local Model Status:\n")
        append("- Available: ${deviceSupported ?: "not checked"}\n")
        append("- Status: $statusMessage\n")
        append("- Model: Gemini Nano (on-device)\n")
        append("- Privacy: All data stays on device\n")
        append("- Tool use: Not supported\n")
    }
}
