package com.openclaw.android.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardTool(private val context: Context) : Tool {

    override val name = "clipboard"
    override val description = "Read from or write to the device clipboard. " +
            "Actions: 'read' (get current clipboard content), 'write' (copy text to clipboard), " +
            "'history' (show recent clipboard entries)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("read"); add("write"); add("history") })
                put("description", "Clipboard action to perform")
            }
            putJsonObject("text") {
                put("type", "string")
                put("description", "Text to copy to clipboard (for 'write' action)")
            }
            putJsonObject("label") {
                put("type", "string")
                put("description", "Label for the clipboard entry (for 'write' action)")
            }
        }
        putJsonArray("required") { add("action") }
    }

    // Simple in-memory clipboard history (persists during app session)
    companion object {
        private val clipHistory = mutableListOf<Pair<String, Long>>() // text, timestamp
        private const val MAX_HISTORY = 20
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        return try {
            when (action) {
                "read" -> readClipboard(clipboard)
                "write" -> {
                    val text = args["text"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'text' for write action")
                    val label = args["label"]?.jsonPrimitive?.contentOrNull ?: "OpenClaw"
                    writeClipboard(clipboard, text, label)
                }
                "history" -> showHistory()
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Clipboard error: ${e.message}")
        }
    }

    private fun readClipboard(clipboard: ClipboardManager): ToolResult {
        if (!clipboard.hasPrimaryClip()) {
            return ToolResult.success("Clipboard is empty.")
        }

        val clip = clipboard.primaryClip ?: return ToolResult.success("Clipboard is empty.")
        val text = clip.getItemAt(0)?.text?.toString()
            ?: clip.getItemAt(0)?.uri?.toString()
            ?: return ToolResult.success("Clipboard contains non-text data.")

        // Track in history
        trackClip(text)

        return ToolResult.success("Clipboard content:\n$text")
    }

    private fun writeClipboard(clipboard: ClipboardManager, text: String, label: String): ToolResult {
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        trackClip(text)
        return ToolResult.success("Copied to clipboard: \"${text.take(100)}\"${if (text.length > 100) "..." else ""}")
    }

    private fun showHistory(): ToolResult {
        if (clipHistory.isEmpty()) {
            return ToolResult.success("No clipboard history available (history is tracked during app session).")
        }

        val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val sb = StringBuilder("Clipboard history (${clipHistory.size} entries):\n\n")

        clipHistory.reversed().forEachIndexed { i, (text, timestamp) ->
            sb.append("${i + 1}. [${dateFormat.format(Date(timestamp))}] ${text.take(80)}")
            if (text.length > 80) sb.append("...")
            sb.append("\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun trackClip(text: String) {
        clipHistory.add(text to System.currentTimeMillis())
        while (clipHistory.size > MAX_HISTORY) {
            clipHistory.removeAt(0)
        }
    }
}
