package com.openclaw.android.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Groq provider. Uses the OpenAI-compatible API.
 * Free tier provides fast inference on open-source models.
 */
class GroqProvider(
    private val apiKeyProvider: () -> String,
) : LlmProvider {

    override val providerId = "groq"
    override val displayName = "Groq"
    override val supportsVision = true
    override val supportsToolUse = true

    override val availableModels = listOf(
        ModelInfo(
            id = "llama-3.3-70b-versatile",
            displayName = "Llama 3.3 70B",
            contextWindow = 128_000,
            supportsVision = false,
            supportsToolUse = true,
        ),
        ModelInfo(
            id = "llama-3.2-90b-vision-preview",
            displayName = "Llama 3.2 90B Vision",
            contextWindow = 128_000,
            supportsVision = true,
            supportsToolUse = true,
        ),
        ModelInfo(
            id = "llama-3.2-11b-vision-preview",
            displayName = "Llama 3.2 11B Vision",
            contextWindow = 128_000,
            supportsVision = true,
            supportsToolUse = true,
        ),
        ModelInfo(
            id = "mixtral-8x7b-32768",
            displayName = "Mixtral 8x7B",
            contextWindow = 32_768,
            supportsVision = false,
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
                onError(IllegalStateException("Groq API key not configured"))
                return@withContext
            }

            val body = buildOpenAiRequestBody(request)
            val httpRequest = Request.Builder()
                .url("https://api.groq.com/openai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: "Unknown error"
                    onError(Exception("Groq API error ${resp.code}: $errorBody"))
                    return@withContext
                }

                parseOpenAiStreamingResponse(resp, onChunk, onToolCall, onDone)
            }
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun buildOpenAiRequestBody(request: ChatRequest): JsonObject =
        buildOpenAiCompatibleBody(request, json)

    private fun parseOpenAiStreamingResponse(
        response: okhttp3.Response,
        onChunk: (String) -> Unit,
        onToolCall: (ToolCallRequest) -> Unit,
        onDone: (ChatResponse) -> Unit,
    ) {
        parseOpenAiCompatibleStream(response, json, onChunk, onToolCall, onDone)
    }
}

// Shared utilities for OpenAI-compatible APIs (Groq, Cerebras, etc.)

internal fun buildOpenAiCompatibleBody(request: ChatRequest, json: Json): JsonObject = buildJsonObject {
    put("model", request.model)
    put("max_tokens", request.settings.maxTokens)
    put("temperature", request.settings.temperature)
    put("top_p", request.settings.topP)
    put("stream", true)

    putJsonArray("messages") {
        // System prompt
        request.systemPrompt?.let { prompt ->
            addJsonObject {
                put("role", "system")
                put("content", prompt)
            }
        }

        for (msg in request.messages) {
            addJsonObject {
                put("role", msg.role)

                when {
                    // Tool result
                    msg.role == "tool" -> {
                        put("tool_call_id", msg.toolCallId)
                        put("content", msg.content.firstOrNull()?.text ?: "")
                    }
                    // Assistant with tool calls
                    msg.role == "assistant" && !msg.toolCalls.isNullOrEmpty() -> {
                        put("content", msg.content.firstOrNull()?.text ?: "")
                        putJsonArray("tool_calls") {
                            for (tc in msg.toolCalls) {
                                addJsonObject {
                                    put("id", tc.id)
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", tc.name)
                                        put("arguments", tc.arguments.toString())
                                    }
                                }
                            }
                        }
                    }
                    // Content with vision
                    msg.content.any { it.type != "text" } -> {
                        putJsonArray("content") {
                            for (part in msg.content) {
                                when (part.type) {
                                    "text" -> addJsonObject {
                                        put("type", "text")
                                        put("text", part.text)
                                    }
                                    "image_base64" -> addJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") {
                                            put("url", "data:${part.mediaType ?: "image/jpeg"};base64,${part.data}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Plain text
                    else -> {
                        put("content", msg.content.firstOrNull()?.text ?: "")
                    }
                }
            }
        }
    }

    // Tools
    if (!request.tools.isNullOrEmpty()) {
        putJsonArray("tools") {
            for (tool in request.tools) {
                addJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.parameters)
                    }
                }
            }
        }
    }
}

internal fun parseOpenAiCompatibleStream(
    response: okhttp3.Response,
    json: Json,
    onChunk: (String) -> Unit,
    onToolCall: (ToolCallRequest) -> Unit,
    onDone: (ChatResponse) -> Unit,
) {
    val fullText = StringBuilder()
    val toolCalls = mutableMapOf<Int, Triple<String, String, StringBuilder>>() // index -> (id, name, args)
    var totalPromptTokens = 0
    var totalCompletionTokens = 0

    response.body?.source()?.let { source ->
        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break

            if (line.startsWith("data: ")) {
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                if (data.isEmpty()) continue

                try {
                    val chunk = json.parseToJsonElement(data).jsonObject
                    val choices = chunk["choices"]?.jsonArray ?: continue

                    for (choice in choices) {
                        val delta = choice.jsonObject["delta"]?.jsonObject ?: continue

                        // Text content
                        delta["content"]?.jsonPrimitive?.contentOrNull?.let { text ->
                            fullText.append(text)
                            onChunk(text)
                        }

                        // Tool calls
                        delta["tool_calls"]?.jsonArray?.forEach { tc ->
                            val tcObj = tc.jsonObject
                            val index = tcObj["index"]?.jsonPrimitive?.intOrNull ?: 0
                            val id = tcObj["id"]?.jsonPrimitive?.contentOrNull
                            val function = tcObj["function"]?.jsonObject

                            if (id != null) {
                                val name = function?.get("name")?.jsonPrimitive?.content ?: ""
                                toolCalls[index] = Triple(id, name, StringBuilder())
                            }

                            function?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { args ->
                                toolCalls[index]?.let { (_, _, sb) -> sb.append(args) }
                            }
                        }
                    }

                    // Usage
                    chunk["usage"]?.jsonObject?.let { usage ->
                        totalPromptTokens = usage["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: totalPromptTokens
                        totalCompletionTokens = usage["completion_tokens"]?.jsonPrimitive?.intOrNull ?: totalCompletionTokens
                    }
                } catch (_: Exception) {
                    // Skip malformed chunks
                }
            }
        }
    }

    val resolvedToolCalls = toolCalls.values.map { (id, name, argsSb) ->
        val argsJson = try {
            json.parseToJsonElement(argsSb.toString())
        } catch (_: Exception) {
            JsonObject(emptyMap())
        }
        val toolCall = ToolCallRequest(id = id, name = name, arguments = argsJson)
        onToolCall(toolCall)
        toolCall
    }

    onDone(
        ChatResponse(
            content = fullText.toString(),
            toolCalls = resolvedToolCalls,
            usage = TokenUsage(totalPromptTokens, totalCompletionTokens, totalPromptTokens + totalCompletionTokens),
            finishReason = if (resolvedToolCalls.isNotEmpty()) "tool_calls" else "stop",
        )
    )
}
