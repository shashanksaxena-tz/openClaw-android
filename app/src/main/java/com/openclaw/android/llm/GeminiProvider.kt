package com.openclaw.android.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Google Gemini provider. Uses the Gemini REST API with streaming.
 * Free tier: 15 RPM, 1500 RPD, 1M TPM for Gemini 2.0 Flash.
 */
class GeminiProvider(
    private val apiKeyProvider: () -> String,
) : LlmProvider {

    override val providerId = "gemini"
    override val displayName = "Google Gemini"
    override val supportsVision = true
    override val supportsToolUse = true

    override val availableModels = listOf(
        ModelInfo(
            id = "gemini-2.0-flash",
            displayName = "Gemini 2.0 Flash",
            contextWindow = 1_048_576,
            supportsVision = true,
            supportsToolUse = true,
        ),
        ModelInfo(
            id = "gemini-2.0-flash-lite",
            displayName = "Gemini 2.0 Flash Lite",
            contextWindow = 1_048_576,
            supportsVision = true,
            supportsToolUse = true,
        ),
        ModelInfo(
            id = "gemini-1.5-pro",
            displayName = "Gemini 1.5 Pro",
            contextWindow = 2_097_152,
            supportsVision = true,
            supportsToolUse = true,
        ),
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun isConfigured(): Boolean = apiKeyProvider().isNotBlank()

    override suspend fun chatCompletion(
        request: ChatRequest,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onDone: (ChatResponse) -> Unit,
        onError: (Exception) -> Unit,
    ) = withContext(Dispatchers.IO) {
        try {
            val apiKey = apiKeyProvider()
            if (apiKey.isBlank()) {
                onError(IllegalStateException("Gemini API key not configured"))
                return@withContext
            }

            val model = request.model
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"

            val body = buildGeminiRequestBody(request)
            val httpRequest = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                onError(Exception("Gemini API error ${response.code}: $errorBody"))
                return@withContext
            }

            val fullText = StringBuilder()
            val toolCalls = mutableListOf<ToolCallRequest>()
            var totalPromptTokens = 0
            var totalCompletionTokens = 0

            response.body?.source()?.let { source ->
                val buffer = StringBuilder()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break

                    if (line.startsWith("data: ")) {
                        val data = line.removePrefix("data: ").trim()
                        if (data.isEmpty()) continue

                        try {
                            val chunk = json.parseToJsonElement(data).jsonObject
                            val candidates = chunk["candidates"]?.jsonArray ?: continue

                            for (candidate in candidates) {
                                val content = candidate.jsonObject["content"]?.jsonObject ?: continue
                                val parts = content["parts"]?.jsonArray ?: continue

                                for (part in parts) {
                                    val partObj = part.jsonObject

                                    // Text content
                                    partObj["text"]?.jsonPrimitive?.contentOrNull?.let { text ->
                                        fullText.append(text)
                                        onChunk(text)
                                    }

                                    // Function call (tool use)
                                    partObj["functionCall"]?.jsonObject?.let { fc ->
                                        val name = fc["name"]?.jsonPrimitive?.content ?: return@let
                                        val args = fc["args"] ?: JsonObject(emptyMap())
                                        val toolCall = ToolCallRequest(
                                            id = UUID.randomUUID().toString(),
                                            name = name,
                                            arguments = args,
                                        )
                                        toolCalls.add(toolCall)
                                        onToolCall(toolCall)
                                    }
                                }
                            }

                            // Usage metadata
                            chunk["usageMetadata"]?.jsonObject?.let { usage ->
                                totalPromptTokens = usage["promptTokenCount"]?.jsonPrimitive?.intOrNull ?: totalPromptTokens
                                totalCompletionTokens = usage["candidatesTokenCount"]?.jsonPrimitive?.intOrNull ?: totalCompletionTokens
                            }
                        } catch (_: Exception) {
                            // Skip malformed chunks
                        }
                    }
                }
            }

            onDone(
                ChatResponse(
                    content = fullText.toString(),
                    toolCalls = toolCalls,
                    usage = TokenUsage(totalPromptTokens, totalCompletionTokens, totalPromptTokens + totalCompletionTokens),
                    finishReason = if (toolCalls.isNotEmpty()) "tool_calls" else "stop",
                )
            )
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun buildGeminiRequestBody(request: ChatRequest): JsonObject = buildJsonObject {
        // Contents (messages)
        putJsonArray("contents") {
            for (msg in request.messages) {
                if (msg.role == "tool") {
                    // Tool results go as functionResponse
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject {
                                putJsonObject("functionResponse") {
                                    put("name", msg.toolCallId ?: "unknown")
                                    putJsonObject("response") {
                                        put("result", msg.content.firstOrNull()?.text ?: "")
                                    }
                                }
                            }
                        }
                    }
                    continue
                }

                addJsonObject {
                    put("role", if (msg.role == "assistant") "model" else "user")
                    putJsonArray("parts") {
                        for (part in msg.content) {
                            when (part.type) {
                                "text" -> addJsonObject {
                                    put("text", part.text)
                                }
                                "image_base64" -> addJsonObject {
                                    putJsonObject("inlineData") {
                                        put("mimeType", part.mediaType ?: "image/jpeg")
                                        put("data", part.data)
                                    }
                                }
                                "audio_base64" -> addJsonObject {
                                    putJsonObject("inlineData") {
                                        put("mimeType", part.mediaType ?: "audio/wav")
                                        put("data", part.data)
                                    }
                                }
                            }
                        }

                        // If assistant had tool calls, include them
                        msg.toolCalls?.forEach { tc ->
                            addJsonObject {
                                putJsonObject("functionCall") {
                                    put("name", tc.name)
                                    put("args", tc.arguments)
                                }
                            }
                        }
                    }
                }
            }
        }

        // System instruction
        request.systemPrompt?.let { prompt ->
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    addJsonObject { put("text", prompt) }
                }
            }
        }

        // Tools
        if (!request.tools.isNullOrEmpty()) {
            putJsonArray("tools") {
                addJsonObject {
                    putJsonArray("functionDeclarations") {
                        for (tool in request.tools) {
                            addJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        }
                    }
                }
            }
        }

        // Generation config
        putJsonObject("generationConfig") {
            put("maxOutputTokens", request.maxTokens)
            put("temperature", request.temperature)
        }
    }
}
