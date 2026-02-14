package com.openclaw.android.agent

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Smart notification system — schedules recurring or one-time AI notifications.
 * The AI can schedule notifications like "Remind me at 8am every day" or
 * "Notify me about weather at 7am tomorrow".
 */
class SmartNotificationManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("smart_notifications", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ScheduledNotification(
        val id: Int,
        val title: String,
        val message: String,
        val triggerTimeMs: Long,
        val repeatIntervalMs: Long = 0, // 0 = one-time
        val prompt: String = "",        // optional AI prompt to execute on trigger
    )

    fun schedule(notification: ScheduledNotification) {
        // Save to prefs
        val notifications = getAllScheduled().toMutableList()
        notifications.removeAll { it.id == notification.id }
        notifications.add(notification)
        saveAll(notifications)

        // Schedule alarm
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("notification_id", notification.id)
            putExtra("title", notification.title)
            putExtra("message", notification.message)
            putExtra("prompt", notification.prompt)
        }
        val pending = PendingIntent.getBroadcast(
            context, notification.id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (notification.repeatIntervalMs > 0) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    notification.triggerTimeMs,
                    notification.repeatIntervalMs,
                    pending
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    notification.triggerTimeMs,
                    pending
                )
            }
        } catch (e: SecurityException) {
            // On Android 12+ exact alarms need permission
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                notification.triggerTimeMs,
                pending
            )
        }
    }

    fun cancel(id: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)

        val notifications = getAllScheduled().toMutableList()
        notifications.removeAll { it.id == id }
        saveAll(notifications)
    }

    fun getAllScheduled(): List<ScheduledNotification> {
        val raw = prefs.getString("scheduled_list", "[]") ?: "[]"
        return try {
            json.decodeFromString<List<ScheduledNotification>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAll(list: List<ScheduledNotification>) {
        prefs.edit().putString("scheduled_list", json.encodeToString(list)).apply()
    }

    fun nextId(): Int = (getAllScheduled().maxOfOrNull { it.id } ?: 1000) + 1
}

/**
 * Receives scheduled notification broadcasts and shows them.
 */
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "OpenClaw"
        val message = intent.getStringExtra("message") ?: "You have a reminder"
        NotificationHelper.showSmartNotification(context, title, message)
    }
}
