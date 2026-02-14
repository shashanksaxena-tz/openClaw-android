package com.openclaw.android.data

import android.content.Context
import android.content.SharedPreferences
import com.openclaw.android.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Auto-delete conversations older than a configurable number of days.
 * Runs on app startup and can be triggered manually.
 */
class ConversationExpiry(
    private val context: Context,
    private val database: AppDatabase,
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("conversation_expiry", Context.MODE_PRIVATE)

    /** Expiry period in days. 0 = never expire */
    var expiryDays: Int
        get() = prefs.getInt("expiry_days", 0)
        set(value) { prefs.edit().putInt("expiry_days", value).apply() }

    /** Whether expiry is enabled */
    val isEnabled: Boolean get() = expiryDays > 0

    /**
     * Delete all conversations older than [expiryDays].
     * Returns number of conversations deleted.
     */
    suspend fun purgeExpired(): Int = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext 0

        val cutoff = System.currentTimeMillis() - expiryDays.toLong() * 24 * 60 * 60 * 1000
        val dao = database.conversationDao()

        // Get conversations older than cutoff
        val expired = dao.getExpiredConversations(cutoff)
        var deleted = 0

        expired.forEach { conv ->
            dao.deleteMessages(conv.id)
            dao.deleteConversation(conv.id)
            deleted++
        }

        // Update last purge time
        prefs.edit().putLong("last_purge", System.currentTimeMillis()).apply()

        deleted
    }

    /** Get the last time purge was run */
    fun getLastPurge(): Long = prefs.getLong("last_purge", 0)

    /** Get available expiry options */
    fun getOptions(): List<Pair<Int, String>> = listOf(
        0 to "Never",
        1 to "1 day",
        3 to "3 days",
        7 to "1 week",
        14 to "2 weeks",
        30 to "30 days",
        90 to "90 days",
    )
}
