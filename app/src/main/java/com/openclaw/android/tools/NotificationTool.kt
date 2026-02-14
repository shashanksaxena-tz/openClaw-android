package com.openclaw.android.tools

import com.openclaw.android.agent.SmartNotificationManager
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationTool(private val notifManager: SmartNotificationManager) : Tool {

    override val name = "smart_notification"
    override val description = "Schedule smart notifications and reminders. " +
            "Actions: 'schedule' (set a reminder), 'list' (view scheduled), 'cancel' (remove a reminder). " +
            "Can schedule one-time or repeating notifications."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("schedule"); add("list"); add("cancel") })
                put("description", "Notification action")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Notification title")
            }
            putJsonObject("message") {
                put("type", "string")
                put("description", "Notification message body")
            }
            putJsonObject("trigger_time") {
                put("type", "string")
                put("description", "When to trigger, ISO 8601 format (e.g. 2024-03-15T08:00:00)")
            }
            putJsonObject("repeat_interval") {
                put("type", "string")
                put("description", "Repeat interval: 'daily', 'weekly', 'hourly', or 'none' (default)")
            }
            putJsonObject("notification_id") {
                put("type", "integer")
                put("description", "ID of notification to cancel")
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
                "schedule" -> scheduleNotification(args)
                "list" -> listScheduled()
                "cancel" -> {
                    val id = args["notification_id"]?.jsonPrimitive?.intOrNull
                        ?: return ToolResult.error("Missing 'notification_id'")
                    notifManager.cancel(id)
                    ToolResult.success("Cancelled notification #$id")
                }
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Notification error: ${e.message}")
        }
    }

    private fun scheduleNotification(args: JsonObject): ToolResult {
        val title = args["title"]?.jsonPrimitive?.contentOrNull ?: "OpenClaw Reminder"
        val message = args["message"]?.jsonPrimitive?.contentOrNull ?: "You have a reminder"
        val triggerStr = args["trigger_time"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'trigger_time'")
        val repeatStr = args["repeat_interval"]?.jsonPrimitive?.contentOrNull ?: "none"

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val triggerMs = try {
            isoFormat.parse(triggerStr)?.time ?: return ToolResult.error("Invalid trigger_time format")
        } catch (e: Exception) {
            return ToolResult.error("Invalid trigger_time. Use ISO 8601: yyyy-MM-ddTHH:mm:ss")
        }

        if (triggerMs < System.currentTimeMillis()) {
            return ToolResult.error("trigger_time must be in the future")
        }

        val repeatMs = when (repeatStr.lowercase()) {
            "hourly" -> 60L * 60 * 1000
            "daily" -> 24L * 60 * 60 * 1000
            "weekly" -> 7L * 24 * 60 * 60 * 1000
            "none", "" -> 0L
            else -> 0L
        }

        val id = notifManager.nextId()
        notifManager.schedule(
            SmartNotificationManager.ScheduledNotification(
                id = id,
                title = title,
                message = message,
                triggerTimeMs = triggerMs,
                repeatIntervalMs = repeatMs,
            )
        )

        val dateFormat = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        val repeatLabel = if (repeatMs > 0) " (repeats $repeatStr)" else " (one-time)"
        return ToolResult.success("Scheduled: \"$title\" for ${dateFormat.format(Date(triggerMs))}$repeatLabel [ID: $id]")
    }

    private fun listScheduled(): ToolResult {
        val all = notifManager.getAllScheduled()
        if (all.isEmpty()) return ToolResult.success("No scheduled notifications.")

        val dateFormat = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        val sb = StringBuilder("Scheduled notifications (${all.size}):\n\n")

        all.sortedBy { it.triggerTimeMs }.forEach { n ->
            val repeatLabel = when {
                n.repeatIntervalMs >= 7L * 24 * 60 * 60 * 1000 -> "weekly"
                n.repeatIntervalMs >= 24L * 60 * 60 * 1000 -> "daily"
                n.repeatIntervalMs >= 60L * 60 * 1000 -> "hourly"
                else -> "once"
            }
            sb.append("- [#${n.id}] **${n.title}**: ${n.message.take(60)}\n")
            sb.append("  ${dateFormat.format(Date(n.triggerTimeMs))} ($repeatLabel)\n\n")
        }

        return ToolResult.success(sb.toString())
    }
}
