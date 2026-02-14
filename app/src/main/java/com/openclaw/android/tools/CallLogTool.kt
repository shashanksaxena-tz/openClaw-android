package com.openclaw.android.tools

import android.content.Context
import android.provider.CallLog
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Locale

class CallLogTool(private val context: Context) : Tool {

    override val name = "call_log"
    override val description = "Read call history, analyze call patterns, and search call records. " +
            "Actions: 'recent' (list recent calls), 'search' (search by number/contact), " +
            "'stats' (call frequency analysis — who did I talk to most)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("recent"); add("search"); add("stats") })
                put("description", "Action to perform")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Number of records to return (default 20)")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Phone number or contact name to search")
            }
            putJsonObject("days") {
                put("type", "integer")
                put("description", "Number of days to analyze for stats (default 30)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "recent" -> recentCalls(args["limit"]?.jsonPrimitive?.intOrNull ?: 20)
                "search" -> {
                    val query = args["query"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'query' for search")
                    searchCalls(query)
                }
                "stats" -> callStats(args["days"]?.jsonPrimitive?.intOrNull ?: 30)
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Call log permission not granted. Please grant call log access in Settings.")
        } catch (e: Exception) {
            ToolResult.error("Call log error: ${e.message}")
        }
    }

    private fun recentCalls(limit: Int): ToolResult {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit"
        ) ?: return ToolResult.success("No call history found.")

        val sb = StringBuilder("Recent calls:\n\n")
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(0) ?: "Unknown"
                val name = it.getString(1)
                val date = it.getLong(2)
                val duration = it.getLong(3)
                val type = it.getInt(4)

                val typeStr = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "Incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    CallLog.Calls.REJECTED_TYPE -> "Rejected"
                    else -> "Other"
                }
                val displayName = if (name != null) "$name ($number)" else number
                val durationStr = formatDuration(duration)

                sb.append("- **$displayName** — $typeStr, ${dateFormat.format(date)}, $durationStr\n")
            }
        }

        return ToolResult.success(sb.toString())
    }

    private fun searchCalls(query: String): ToolResult {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
            ),
            "${CallLog.Calls.NUMBER} LIKE ? OR ${CallLog.Calls.CACHED_NAME} LIKE ?",
            arrayOf("%$query%", "%$query%"),
            "${CallLog.Calls.DATE} DESC LIMIT 20"
        ) ?: return ToolResult.success("No calls matching '$query'.")

        val sb = StringBuilder("Calls matching '$query':\n\n")
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(0) ?: "Unknown"
                val name = it.getString(1)
                val date = it.getLong(2)
                val duration = it.getLong(3)
                val type = it.getInt(4)
                val typeStr = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "In"
                    CallLog.Calls.OUTGOING_TYPE -> "Out"
                    CallLog.Calls.MISSED_TYPE -> "Missed"
                    else -> "?"
                }
                val displayName = name ?: number
                sb.append("- **$displayName** $typeStr ${dateFormat.format(date)} (${formatDuration(duration)})\n")
            }
        }

        return ToolResult.success(sb.toString())
    }

    private fun callStats(days: Int): ToolResult {
        val resolver = context.contentResolver
        val since = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000

        val cursor = resolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
            ),
            "${CallLog.Calls.DATE} >= ?",
            arrayOf(since.toString()),
            null
        ) ?: return ToolResult.success("No call history in the last $days days.")

        data class ContactStats(
            var name: String,
            var incoming: Int = 0,
            var outgoing: Int = 0,
            var missed: Int = 0,
            var totalDuration: Long = 0,
        )

        val statsMap = mutableMapOf<String, ContactStats>()

        cursor.use {
            while (it.moveToNext()) {
                val number = it.getString(0) ?: "Unknown"
                val name = it.getString(1) ?: number
                val duration = it.getLong(2)
                val type = it.getInt(3)

                val stats = statsMap.getOrPut(number) { ContactStats(name) }
                stats.name = name // Update with latest name
                stats.totalDuration += duration
                when (type) {
                    CallLog.Calls.INCOMING_TYPE -> stats.incoming++
                    CallLog.Calls.OUTGOING_TYPE -> stats.outgoing++
                    CallLog.Calls.MISSED_TYPE -> stats.missed++
                }
            }
        }

        val sorted = statsMap.entries.sortedByDescending {
            it.value.incoming + it.value.outgoing + it.value.missed
        }

        val sb = StringBuilder("Call stats (last $days days):\n\n")
        sb.append("**Total contacts called**: ${sorted.size}\n")
        sb.append("**Total calls**: ${sorted.sumOf { it.value.incoming + it.value.outgoing + it.value.missed }}\n\n")
        sb.append("**Top contacts by call frequency**:\n\n")

        sorted.take(15).forEachIndexed { i, (_, stats) ->
            val total = stats.incoming + stats.outgoing + stats.missed
            sb.append("${i + 1}. **${stats.name}** — $total calls ")
            sb.append("(${stats.incoming} in, ${stats.outgoing} out, ${stats.missed} missed) ")
            sb.append("Total talk time: ${formatDuration(stats.totalDuration)}\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun formatDuration(seconds: Long): String = when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
