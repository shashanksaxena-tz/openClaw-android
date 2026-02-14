package com.openclaw.android.tools

import com.openclaw.android.data.PrivacyAudit
import kotlinx.serialization.json.*

class PrivacyAuditTool(private val audit: PrivacyAudit) : Tool {

    override val name = "privacy_audit"
    override val description = "View privacy information — see what data has been sent to which APIs. " +
            "Actions: 'report' (full privacy report), 'recent' (recent API calls), 'clear' (clear audit log)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("report"); add("recent"); add("clear") })
                put("description", "Privacy audit action")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Number of recent calls to show (default 10)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return when (action) {
            "report" -> ToolResult.success(audit.getReport())
            "recent" -> {
                val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 10
                val calls = audit.getRecentCalls().takeLast(limit)
                if (calls.isEmpty()) {
                    ToolResult.success("No API calls recorded yet.")
                } else {
                    val sb = StringBuilder("Recent API calls (${calls.size}):\n\n")
                    calls.reversed().forEach { c ->
                        val time = java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault())
                            .format(java.util.Date(c.timestamp))
                        sb.append("- **${c.provider}/${c.model}** at $time\n")
                        sb.append("  Data: ${c.dataTypes.joinToString(", ")}")
                        if (c.toolsUsed.isNotEmpty()) sb.append(" | Tools: ${c.toolsUsed.joinToString(", ")}")
                        sb.append("\n")
                    }
                    ToolResult.success(sb.toString())
                }
            }
            "clear" -> {
                audit.clearAuditLog()
                ToolResult.success("Privacy audit log cleared.")
            }
            else -> ToolResult.error("Unknown action: $action")
        }
    }
}
