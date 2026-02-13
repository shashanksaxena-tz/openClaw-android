package com.openclaw.android.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class FetchUrlTool : Tool {

    override val name = "fetch_url"
    override val description = "Fetch the content of a URL and return it as text. " +
            "Useful for reading web pages, APIs, or downloading text content."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("url") {
                put("type", "string")
                put("description", "The URL to fetch")
            }
            putJsonObject("max_length") {
                put("type", "integer")
                put("description", "Maximum response length in characters. Default: 10000")
            }
        }
        putJsonArray("required") { add("url") }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun execute(arguments: JsonElement): ToolResult = withContext(Dispatchers.IO) {
        val args = arguments.jsonObject
        val url = args["url"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext ToolResult.error("Missing 'url' parameter")
        val maxLength = args["max_length"]?.jsonPrimitive?.intOrNull ?: 10_000

        try {
            // Block requests to private/internal IPs
            val parsedUrl = java.net.URL(url)
            val host = parsedUrl.host?.lowercase() ?: ""
            if (host == "localhost" || host == "127.0.0.1" || host.startsWith("192.168.") ||
                host.startsWith("10.") || host.startsWith("172.16.") || host.endsWith(".local")) {
                return@withContext ToolResult.error("Cannot fetch internal/private network addresses")
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "OpenClaw-Android/0.1")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext ToolResult.error("HTTP ${response.code}: ${response.message}")
            }

            val contentType = response.header("Content-Type") ?: ""
            val body = response.body?.string() ?: ""

            // Basic HTML to text conversion
            val text = if (contentType.contains("html")) {
                stripHtml(body)
            } else {
                body
            }

            val truncated = if (text.length > maxLength) {
                text.take(maxLength) + "\n... (truncated, ${text.length} total chars)"
            } else {
                text
            }

            ToolResult.success(truncated)
        } catch (e: Exception) {
            ToolResult.error("Failed to fetch URL: ${e.message}")
        }
    }

    private fun stripHtml(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>"), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>"), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("&amp;"), "&")
            .replace(Regex("&lt;"), "<")
            .replace(Regex("&gt;"), ">")
            .replace(Regex("&quot;"), "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
