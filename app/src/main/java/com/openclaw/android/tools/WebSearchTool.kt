package com.openclaw.android.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Web search tool using DuckDuckGo's HTML search (no API key needed).
 * Falls back to fetching URL content directly if a URL is provided.
 */
class WebSearchTool : Tool {

    override val name = "web_search"
    override val description = "Search the web for information. Can also extract content from URLs. " +
            "Use this when the user asks about current events, facts, or shares links."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query or URL to fetch content from")
            }
        }
        putJsonArray("required") { add("query") }
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override suspend fun execute(arguments: JsonElement): ToolResult = withContext(Dispatchers.IO) {
        val query = arguments.jsonObject["query"]?.jsonPrimitive?.contentOrNull
            ?: return@withContext ToolResult.error("Missing 'query' parameter")

        // If it looks like a URL, fetch it directly
        if (query.startsWith("http://") || query.startsWith("https://")) {
            return@withContext fetchUrl(query)
        }

        // Otherwise, search using DuckDuckGo HTML
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://html.duckduckgo.com/html/?q=$encoded"

            val request = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", "Mozilla/5.0 (Android; OpenClaw)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext ToolResult.error("Empty search response")

            val results = parseSearchResults(body)
            if (results.isEmpty()) {
                ToolResult.success("No search results found for: $query")
            } else {
                ToolResult.success("Search results for '$query':\n\n${results.joinToString("\n\n")}")
            }
        } catch (e: Exception) {
            ToolResult.error("Search failed: ${e.message}")
        }
    }

    private fun fetchUrl(url: String): ToolResult {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; OpenClaw)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return ToolResult.error("HTTP ${response.code}: ${response.message}")
            }

            val contentType = response.header("Content-Type") ?: ""
            val body = response.body?.string() ?: ""

            val text = if (contentType.contains("html")) {
                extractMainContent(body)
            } else {
                body
            }

            val truncated = text.take(8000)
            ToolResult.success(if (text.length > 8000) "$truncated\n\n[Truncated - ${text.length} total chars]" else truncated)
        } catch (e: Exception) {
            ToolResult.error("Failed to fetch URL: ${e.message}")
        }
    }

    private fun parseSearchResults(html: String): List<String> {
        val results = mutableListOf<String>()
        val resultPattern = Regex("""<a[^>]+class="result__a"[^>]*href="([^"]*)"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        val snippetPattern = Regex("""<a[^>]+class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

        val titles = resultPattern.findAll(html).toList()
        val snippets = snippetPattern.findAll(html).toList()

        for (i in titles.indices.take(5)) {
            val title = titles[i].groupValues[2].replace(Regex("<[^>]+>"), "").trim()
            val url = titles[i].groupValues[1]
            val snippet = snippets.getOrNull(i)?.groupValues?.get(1)
                ?.replace(Regex("<[^>]+>"), "")?.trim() ?: ""

            results.add("**$title**\n$url\n$snippet")
        }
        return results
    }

    private fun extractMainContent(html: String): String {
        return html
            .replace(Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<style[^>]*>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<nav[^>]*>[\\s\\S]*?</nav>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<header[^>]*>[\\s\\S]*?</header>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<footer[^>]*>[\\s\\S]*?</footer>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;|&amp;|&lt;|&gt;|&quot;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
