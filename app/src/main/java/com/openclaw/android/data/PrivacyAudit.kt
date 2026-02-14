package com.openclaw.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Privacy audit system — tracks what data is sent to which APIs.
 * Provides transparency to the user about their data flow.
 */
class PrivacyAudit(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("privacy_audit", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class ApiCall(
        val timestamp: Long,
        val provider: String,
        val model: String,
        val dataTypes: List<String>,   // "text", "image", "audio", "tool_result"
        val tokensSent: Int = 0,
        val tokensReceived: Int = 0,
        val toolsUsed: List<String> = emptyList(),
    )

    @Serializable
    data class AuditSummary(
        val totalCalls: Int,
        val byProvider: Map<String, Int>,
        val dataTypesSent: Map<String, Int>,
        val toolsUsed: Map<String, Int>,
        val firstCall: Long,
        val lastCall: Long,
    )

    fun logApiCall(
        provider: String,
        model: String,
        dataTypes: List<String>,
        tokensSent: Int = 0,
        tokensReceived: Int = 0,
        toolsUsed: List<String> = emptyList(),
    ) {
        val calls = getRecentCalls().toMutableList()
        calls.add(ApiCall(
            timestamp = System.currentTimeMillis(),
            provider = provider,
            model = model,
            dataTypes = dataTypes,
            tokensSent = tokensSent,
            tokensReceived = tokensReceived,
            toolsUsed = toolsUsed,
        ))

        // Keep last 500 calls
        while (calls.size > 500) calls.removeAt(0)
        saveCalls(calls)

        // Update counters
        val totalCalls = prefs.getInt("total_calls", 0)
        prefs.edit().putInt("total_calls", totalCalls + 1).apply()
    }

    fun getRecentCalls(): List<ApiCall> {
        val raw = prefs.getString("api_calls", "[]") ?: "[]"
        return try {
            json.decodeFromString<List<ApiCall>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSummary(): AuditSummary {
        val calls = getRecentCalls()
        if (calls.isEmpty()) {
            return AuditSummary(0, emptyMap(), emptyMap(), emptyMap(), 0, 0)
        }

        return AuditSummary(
            totalCalls = prefs.getInt("total_calls", calls.size),
            byProvider = calls.groupBy { it.provider }.mapValues { it.value.size },
            dataTypesSent = calls.flatMap { it.dataTypes }.groupBy { it }.mapValues { it.value.size },
            toolsUsed = calls.flatMap { it.toolsUsed }.groupBy { it }.mapValues { it.value.size },
            firstCall = calls.minOf { it.timestamp },
            lastCall = calls.maxOf { it.timestamp },
        )
    }

    fun getReport(): String {
        val summary = getSummary()
        val dateFormat = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

        return buildString {
            append("Privacy Audit Report\n")
            append("====================\n\n")

            append("**Total API calls**: ${summary.totalCalls}\n")
            if (summary.firstCall > 0) {
                append("**Period**: ${dateFormat.format(Date(summary.firstCall))} — ${dateFormat.format(Date(summary.lastCall))}\n")
            }
            append("\n")

            append("**Data sent by provider**:\n")
            summary.byProvider.toList().sortedByDescending { it.second }.forEach { (provider, count) ->
                append("  - $provider: $count calls\n")
            }
            append("\n")

            append("**Data types sent**:\n")
            summary.dataTypesSent.toList().sortedByDescending { it.second }.forEach { (type, count) ->
                append("  - $type: $count times\n")
            }
            append("\n")

            append("**Tools used**:\n")
            if (summary.toolsUsed.isEmpty()) {
                append("  None recorded\n")
            } else {
                summary.toolsUsed.toList().sortedByDescending { it.second }.forEach { (tool, count) ->
                    append("  - $tool: $count times\n")
                }
            }
            append("\n")

            append("**Data destination endpoints**:\n")
            summary.byProvider.keys.forEach { provider ->
                val endpoint = when (provider) {
                    "gemini" -> "generativelanguage.googleapis.com (Google)"
                    "groq" -> "api.groq.com (Groq)"
                    "cerebras" -> "api.cerebras.ai (Cerebras)"
                    "local" -> "ON-DEVICE (no network)"
                    else -> "$provider (unknown)"
                }
                append("  - $endpoint\n")
            }
            append("\n")

            append("**Note**: API keys are encrypted on-device using AES-256.\n")
            append("Conversation data is stored locally in an encrypted Room database.\n")
            append("No telemetry or analytics data is collected by OpenClaw.\n")
        }
    }

    fun clearAuditLog() {
        prefs.edit()
            .putString("api_calls", "[]")
            .putInt("total_calls", 0)
            .apply()
    }

    private fun saveCalls(calls: List<ApiCall>) {
        prefs.edit().putString("api_calls", json.encodeToString(calls)).apply()
    }
}
