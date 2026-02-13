package com.openclaw.android.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Cerebras provider. Uses the OpenAI-compatible API.
 * Free tier provides extremely fast inference.
 */
class CerebrasProvider(
    private val apiKeyProvider: () -> String,
) : LlmProvider {

    override val providerId = "cerebras"
    override val displayName = "Cerebras"
    override val supportsVision = false
    override val supportsToolUse = true

    override val availableModels = listOf(
        ModelInfo(
            id = "llama-3.3-70b",
            displayName = "Llama 3.3 70B",
            contextWindow = 128_000,
            supportsVision = false,
            supportsToolUse = true,
        ),
        ModelInfo(
            id = "llama-3.1-8b",
            displayName = "Llama 3.1 8B",
            contextWindow = 128_000,
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
                onError(IllegalStateException("Cerebras API key not configured"))
                return@withContext
            }

            val body = buildOpenAiCompatibleBody(request, json)
            val httpRequest = Request.Builder()
                .url("https://api.cerebras.ai/v1/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                onError(Exception("Cerebras API error ${response.code}: $errorBody"))
                return@withContext
            }

            parseOpenAiCompatibleStream(response, json, onChunk, onToolCall, onDone)
        } catch (e: Exception) {
            onError(e)
        }
    }
}
