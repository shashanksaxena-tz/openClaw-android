package com.openclaw.android.llm

/**
 * Chat template builder for different model families.
 * Detects the model family from filename and applies the correct template.
 */
object ChatTemplate {

    /** Stop sequences for generation. */
    fun stopSequences(modelPath: String?): List<String> {
        return if (isGemma(modelPath)) {
            listOf("<end_of_turn>", "<start_of_turn>")
        } else {
            listOf("<|im_end|>", "<|im_start|>")
        }
    }

    /** Build a complete prompt string from a ChatRequest. */
    fun build(request: ChatRequest, modelPath: String? = null): String {
        return if (isGemma(modelPath)) buildGemma(request) else buildChatML(request)
    }

    fun isGemma(modelPath: String?): Boolean =
        modelPath?.lowercase()?.contains("gemma") == true

    // ── Gemma: <start_of_turn>/<end_of_turn> ───────────────────────────────

    private fun buildGemma(request: ChatRequest): String = buildString {
        val sys = request.systemPrompt ?: ""
        append("<start_of_turn>user\n[System]\n${sys.trim()}")
        appendTools(request.tools)
        append("\n<end_of_turn>\n")
        append("<start_of_turn>model\nUnderstood.\n<end_of_turn>\n")
        for (msg in request.messages) {
            appendGemmaMessage(msg)
        }
        append("<start_of_turn>model\n")
    }

    private fun StringBuilder.appendGemmaMessage(msg: ChatMessage) {
        when (msg.role) {
            "user" -> {
                append("<start_of_turn>user\n")
                append(msg.textContent())
                appendImageNote(msg)
                append("\n<end_of_turn>\n")
            }
            "assistant" -> {
                append("<start_of_turn>model\n")
                append(msg.textContent())
                appendToolCalls(msg)
                append("\n<end_of_turn>\n")
            }
            "tool" -> {
                append("<start_of_turn>user\nTool result for ${msg.toolCallId}:\n")
                append(msg.textContent())
                append("\n<end_of_turn>\n")
            }
        }
    }

    // ── ChatML: <|im_start|>/<|im_end|> (Qwen, Phi, Llama, SmolLM) ────────

    private fun buildChatML(request: ChatRequest): String = buildString {
        val sys = request.systemPrompt ?: ""
        append("<|im_start|>system\n${sys.trim()}\n<|im_end|>\n")
        if (!request.tools.isNullOrEmpty()) {
            append("<|im_start|>system\n")
            appendTools(request.tools)
            append("\n<|im_end|>\n")
        }
        for (msg in request.messages) {
            appendChatMLMessage(msg)
        }
        append("<|im_start|>assistant\n")
    }

    private fun StringBuilder.appendChatMLMessage(msg: ChatMessage) {
        when (msg.role) {
            "user" -> {
                append("<|im_start|>user\n")
                append(msg.textContent())
                appendImageNote(msg)
                append("\n<|im_end|>\n")
            }
            "assistant" -> {
                append("<|im_start|>assistant\n")
                append(msg.textContent())
                appendToolCalls(msg)
                append("\n<|im_end|>\n")
            }
            "tool" -> {
                append("<|im_start|>tool\nTool result for ${msg.toolCallId}:\n")
                append(msg.textContent())
                append("\n<|im_end|>\n")
            }
        }
    }

    // ── Shared helpers ──────────────────────────────────────────────────────

    private fun StringBuilder.appendTools(tools: List<ToolDefinition>?) {
        if (tools.isNullOrEmpty()) return
        append("\n\nTo call a tool, respond with:\n```tool_call\n{\"name\": \"tool_name\", \"arguments\": {...}}\n```\n\nTools:\n")
        for (tool in tools) {
            append("- ${tool.name}: ${tool.description}\n")
        }
    }

    private fun StringBuilder.appendToolCalls(msg: ChatMessage) {
        msg.toolCalls?.forEach { tc ->
            append("\n```tool_call\n{\"name\": \"${tc.name}\", \"arguments\": ${tc.arguments}}\n```")
        }
    }

    private fun StringBuilder.appendImageNote(msg: ChatMessage) {
        if (msg.content.any { it.type == "image_base64" }) {
            append("\n[Image attached — you cannot see images. Consider escalating.]")
        }
    }

    private fun ChatMessage.textContent(): String =
        content.firstOrNull { it.type == "text" }?.text ?: ""
}
