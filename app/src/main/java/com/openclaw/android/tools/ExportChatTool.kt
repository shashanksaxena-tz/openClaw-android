package com.openclaw.android.tools

import com.openclaw.android.sandbox.SandboxedFileSystem
import kotlinx.serialization.json.*

/**
 * Exports the current chat conversation as a document in the workspace.
 * Supports .md, .txt, and .html formats.
 */
class ExportChatTool(
    private val fs: SandboxedFileSystem,
    private val getConversationText: suspend () -> String,
) : Tool {

    override val name = "export_chat"
    override val description = "Export the current conversation as a document file in the workspace. " +
            "Useful for saving important conversations as reference. Supports .md, .txt, .html formats."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("filename") {
                put("type", "string")
                put("description", "Output filename (e.g., 'meeting-notes.md', 'summary.txt')")
            }
            putJsonObject("format") {
                put("type", "string")
                put("enum", JsonArray(listOf(JsonPrimitive("md"), JsonPrimitive("txt"), JsonPrimitive("html"))))
                put("description", "Output format: md (markdown), txt (plain text), html. Default: md")
            }
            putJsonObject("summary") {
                put("type", "string")
                put("description", "Optional summary to prepend to the exported document")
            }
        }
        putJsonArray("required") { add("filename") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val filename = args["filename"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'filename' parameter")
        val format = args["format"]?.jsonPrimitive?.contentOrNull ?: "md"
        val summary = args["summary"]?.jsonPrimitive?.contentOrNull

        val conversation = getConversationText()
        if (conversation.isBlank()) {
            return ToolResult.error("No conversation to export")
        }

        val content = when (format) {
            "html" -> buildHtml(conversation, summary)
            "txt" -> buildPlainText(conversation, summary)
            else -> buildMarkdown(conversation, summary)
        }

        val file = fs.resolve(filename).getOrElse {
            return ToolResult.error(it.message ?: "Invalid path")
        }

        return try {
            file.parentFile?.mkdirs()
            file.writeText(content)
            ToolResult.success("Exported conversation to workspace/$filename (${content.length} chars)")
        } catch (e: Exception) {
            ToolResult.error("Failed to export: ${e.message}")
        }
    }

    private fun buildMarkdown(conversation: String, summary: String?): String {
        val sb = StringBuilder()
        sb.appendLine("# Conversation Export")
        sb.appendLine("_Exported from OpenClaw on ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}_")
        sb.appendLine()
        if (summary != null) {
            sb.appendLine("## Summary")
            sb.appendLine(summary)
            sb.appendLine()
        }
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine(conversation)
        return sb.toString()
    }

    private fun buildPlainText(conversation: String, summary: String?): String {
        val sb = StringBuilder()
        sb.appendLine("CONVERSATION EXPORT")
        sb.appendLine("=" .repeat(50))
        if (summary != null) {
            sb.appendLine("\nSUMMARY: $summary\n")
        }
        sb.appendLine(conversation)
        return sb.toString()
    }

    private fun buildHtml(conversation: String, summary: String?): String {
        val escaped = conversation
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")

        return """
            <!DOCTYPE html>
            <html><head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>OpenClaw Conversation</title>
            <style>
                body { font-family: -apple-system, sans-serif; max-width: 700px; margin: 0 auto; padding: 20px; }
                h1 { color: #FF6B35; }
                .summary { background: #FFF3ED; padding: 16px; border-radius: 8px; margin: 16px 0; }
                .conversation { white-space: pre-wrap; line-height: 1.6; }
            </style>
            </head><body>
            <h1>Conversation Export</h1>
            ${if (summary != null) "<div class='summary'><strong>Summary:</strong> $summary</div>" else ""}
            <hr>
            <div class='conversation'>$escaped</div>
            </body></html>
        """.trimIndent()
    }
}
