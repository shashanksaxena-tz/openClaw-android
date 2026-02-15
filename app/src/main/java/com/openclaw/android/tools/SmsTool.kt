package com.openclaw.android.tools

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Locale

class SmsTool(private val context: Context) : Tool {

    override val name = "sms"

    override val requiredPermissions = listOf(Manifest.permission.READ_SMS)
    override val description = "Read recent SMS messages, search messages, or send a new text message. " +
            "Actions: 'inbox' (read recent messages), 'search' (search messages), 'send' (compose a message)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("inbox"); add("search"); add("send") })
                put("description", "Action to perform")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put("description", "Number of messages to return (default 20)")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query for message body or address")
            }
            putJsonObject("phone_number") {
                put("type", "string")
                put("description", "Phone number to send message to")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Message body to send")
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
                "inbox" -> readInbox(args["limit"]?.jsonPrimitive?.intOrNull ?: 20)
                "search" -> {
                    val query = args["query"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'query' for search")
                    searchMessages(query)
                }
                "send" -> {
                    val number = args["phone_number"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'phone_number' for send")
                    val message = args["message"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'message' for send")
                    sendMessage(number, message)
                }
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: SecurityException) {
            ToolResult.error("SMS permission not granted. Please grant SMS access in Settings.")
        } catch (e: Exception) {
            ToolResult.error("SMS error: ${e.message}")
        }
    }

    private fun readInbox(limit: Int): ToolResult {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
            ),
            null, null,
            "${Telephony.Sms.DATE} DESC LIMIT $limit"
        ) ?: return ToolResult.success("No messages found.")

        val sb = StringBuilder("Recent messages:\n\n")
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

        cursor.use {
            while (it.moveToNext()) {
                val address = it.getString(0) ?: "Unknown"
                val body = it.getString(1) ?: ""
                val date = it.getLong(2)
                val type = it.getInt(3)
                val direction = if (type == Telephony.Sms.MESSAGE_TYPE_SENT) "Sent" else "Received"

                sb.append("**$address** ($direction, ${dateFormat.format(date)}):\n")
                sb.append("  ${body.take(200)}${if (body.length > 200) "..." else ""}\n\n")
            }
        }

        return ToolResult.success(sb.toString())
    }

    private fun searchMessages(query: String): ToolResult {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
            ),
            "${Telephony.Sms.BODY} LIKE ? OR ${Telephony.Sms.ADDRESS} LIKE ?",
            arrayOf("%$query%", "%$query%"),
            "${Telephony.Sms.DATE} DESC LIMIT 20"
        ) ?: return ToolResult.success("No messages matching '$query'.")

        val sb = StringBuilder("Messages matching '$query':\n\n")
        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        var count = 0

        cursor.use {
            while (it.moveToNext()) {
                val address = it.getString(0) ?: "Unknown"
                val body = it.getString(1) ?: ""
                val date = it.getLong(2)
                sb.append("**$address** (${dateFormat.format(date)}):\n  ${body.take(200)}\n\n")
                count++
            }
        }

        if (count == 0) sb.append("No messages found.")
        return ToolResult.success(sb.toString())
    }

    private fun sendMessage(number: String, message: String): ToolResult {
        // Open SMS app with pre-filled message instead of sending directly
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(number)}")
            putExtra("sms_body", message)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolResult.success("Opened SMS composer to $number with message: \"${message.take(100)}\"")
    }
}
