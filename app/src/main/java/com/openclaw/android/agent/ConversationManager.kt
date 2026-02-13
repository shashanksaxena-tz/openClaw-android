package com.openclaw.android.agent

import com.openclaw.android.llm.ChatMessage
import com.openclaw.android.llm.ContentPart
import com.openclaw.android.llm.ToolCallRequest

/**
 * Manages conversation history with automatic context window management.
 * Keeps messages within a token budget by dropping old messages when needed.
 */
class ConversationManager(
    private val maxMessages: Int = 50,
) {
    private val _messages = mutableListOf<ChatMessage>()
    val messages: List<ChatMessage> get() = _messages.toList()

    fun addUserMessage(text: String, media: List<ContentPart> = emptyList()) {
        val parts = mutableListOf<ContentPart>()
        if (text.isNotBlank()) {
            parts.add(ContentPart(type = "text", text = text))
        }
        parts.addAll(media)

        _messages.add(ChatMessage(role = "user", content = parts))
        trimIfNeeded()
    }

    fun addAssistantMessage(text: String, toolCalls: List<ToolCallRequest>? = null) {
        val parts = if (text.isNotBlank()) {
            listOf(ContentPart(type = "text", text = text))
        } else {
            emptyList()
        }
        _messages.add(ChatMessage(role = "assistant", content = parts, toolCalls = toolCalls))
        trimIfNeeded()
    }

    fun addToolResult(toolCallId: String, toolName: String, result: String) {
        _messages.add(
            ChatMessage(
                role = "tool",
                content = listOf(ContentPart(type = "text", text = result)),
                toolCallId = toolName,
            )
        )
        trimIfNeeded()
    }

    fun clear() {
        _messages.clear()
    }

    fun getMessagesForRequest(): List<ChatMessage> = _messages.toList()

    /**
     * Export the conversation as human-readable text for the export_chat tool.
     */
    fun getConversationAsText(): String {
        val sb = StringBuilder()
        for (msg in _messages) {
            when (msg.role) {
                "user" -> {
                    sb.appendLine("**User:**")
                    for (part in msg.content) {
                        when (part.type) {
                            "text" -> sb.appendLine(part.text)
                            "image_base64" -> sb.appendLine("[Image attachment]")
                            "audio_base64" -> sb.appendLine("[Audio attachment]")
                        }
                    }
                    sb.appendLine()
                }
                "assistant" -> {
                    sb.appendLine("**Assistant:**")
                    for (part in msg.content) {
                        if (part.type == "text") sb.appendLine(part.text)
                    }
                    msg.toolCalls?.forEach { tc ->
                        sb.appendLine("[Used tool: ${tc.name}]")
                    }
                    sb.appendLine()
                }
                "tool" -> {
                    sb.appendLine("**Tool (${msg.toolCallId}):**")
                    sb.appendLine(msg.content.firstOrNull()?.text?.take(500) ?: "")
                    sb.appendLine()
                }
            }
        }
        return sb.toString().trim()
    }

    fun estimateTokens(): Int {
        var tokens = 0
        for (msg in _messages) {
            for (part in msg.content) {
                when (part.type) {
                    "text" -> tokens += (part.text?.length ?: 0) / 4
                    "image_base64" -> tokens += 1000
                    "audio_base64" -> tokens += 500
                }
            }
        }
        return tokens
    }

    private fun trimIfNeeded() {
        while (_messages.size > maxMessages) {
            _messages.removeAt(0)
        }
    }
}
