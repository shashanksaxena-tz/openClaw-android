package com.openclaw.android.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.serialization.json.*

class EmailTool(private val context: Context) : Tool {

    override val name = "email"
    override val description = "Compose and send emails using the device's email app. " +
            "Actions: 'compose' (draft a new email), 'mailto' (open email to a specific address)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("compose"); add("mailto") })
                put("description", "Email action to perform")
            }
            putJsonObject("to") {
                put("type", "string")
                put("description", "Recipient email address (or comma-separated list)")
            }
            putJsonObject("cc") {
                put("type", "string")
                put("description", "CC email addresses (comma-separated)")
            }
            putJsonObject("bcc") {
                put("type", "string")
                put("description", "BCC email addresses (comma-separated)")
            }
            putJsonObject("subject") {
                put("type", "string")
                put("description", "Email subject line")
            }
            putJsonObject("body") {
                put("type", "string")
                put("description", "Email body text")
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
                "compose" -> composeEmail(args)
                "mailto" -> {
                    val to = args["to"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'to' for mailto")
                    openMailTo(to)
                }
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Email error: ${e.message}")
        }
    }

    private fun composeEmail(args: JsonObject): ToolResult {
        val to = args["to"]?.jsonPrimitive?.contentOrNull
        val cc = args["cc"]?.jsonPrimitive?.contentOrNull
        val bcc = args["bcc"]?.jsonPrimitive?.contentOrNull
        val subject = args["subject"]?.jsonPrimitive?.contentOrNull ?: ""
        val body = args["body"]?.jsonPrimitive?.contentOrNull ?: ""

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            if (to != null) putExtra(Intent.EXTRA_EMAIL, to.split(",").map { it.trim() }.toTypedArray())
            if (cc != null) putExtra(Intent.EXTRA_CC, cc.split(",").map { it.trim() }.toTypedArray())
            if (bcc != null) putExtra(Intent.EXTRA_BCC, bcc.split(",").map { it.trim() }.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(intent)
        val summary = buildString {
            append("Opened email composer")
            if (to != null) append(" to $to")
            if (subject.isNotBlank()) append(" — \"$subject\"")
        }
        return ToolResult.success(summary)
    }

    private fun openMailTo(address: String): ToolResult {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:${Uri.encode(address)}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
        return ToolResult.success("Opened email to $address")
    }
}
