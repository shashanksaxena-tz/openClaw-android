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
                toolCallId = toolName, // Gemini uses the function name, OpenAI uses the call ID
            )
        )
        trimIfNeeded()
    }

    fun clear() {
        _messages.clear()
    }

    /** Get messages trimmed to fit within the context. */
    fun getMessagesForRequest(): List<ChatMessage> = _messages.toList()

    /**
     * Estimate token count for a message (rough approximation: ~4 chars per token).
     * Images count as ~1000 tokens each.
     */
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
        // Keep at most maxMessages, but always keep at least the last user message
        while (_messages.size > maxMessages) {
            // Don't remove if only the system-relevant messages remain
            _messages.removeAt(0)
        }
    }
}
