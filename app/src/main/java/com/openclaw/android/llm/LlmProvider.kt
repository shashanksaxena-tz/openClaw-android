package com.openclaw.android.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Unified interface for all LLM providers (Gemini, Groq, Cerebras).
 * Each provider implements streaming chat completion with tool use support.
 */
interface LlmProvider {
    val providerId: String
    val displayName: String
    val availableModels: List<ModelInfo>
    val supportsVision: Boolean
    val supportsToolUse: Boolean

    suspend fun chatCompletion(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit,
    )

    fun isConfigured(): Boolean
}

@Serializable
data class ModelInfo(
    val id: String,
    val displayName: String,
    val contextWindow: Int,
    val supportsVision: Boolean = false,
    val supportsToolUse: Boolean = true,
    val inputPricePerMToken: Double = 0.0,
    val outputPricePerMToken: Double = 0.0,
)

@Serializable
data class ChatSettings(
    val temperature: Double = 0.7,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val maxTokens: Int = 4096,
    val contextWindow: Int? = null,
    val preferredBackend: String? = null, // "cpu", "gpu", "npu"
    val enabledTools: List<String>? = null, // tool names
)

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition>? = null,
    val systemPrompt: String? = null,
    val settings: ChatSettings = ChatSettings(),
)

@Serializable
data class ChatMessage(
    val role: String, // "user", "assistant", "tool"
    val content: List<ContentPart> = emptyList(),
    val toolCallId: String? = null,
    val toolCalls: List<ToolCallRequest>? = null,
)

@Serializable
data class ContentPart(
    val type: String, // "text", "image_base64", "audio_base64"
    val text: String? = null,
    val mediaType: String? = null, // "image/jpeg", "audio/wav", etc.
    val data: String? = null, // base64 encoded
)

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class ToolCallRequest(
    val id: String,
    val name: String,
    val arguments: JsonElement,
)

@Serializable
data class ChatResponse(
    val content: String,
    val toolCalls: List<ToolCallRequest> = emptyList(),
    val usage: TokenUsage? = null,
    val finishReason: String? = null,
)

@Serializable
data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
)
