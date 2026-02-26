package com.openclaw.android.tools

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Personal Productivity Insights tool.
 * Tracks time usage, task completion, meeting load, focus time,
 * delegation ratio, and productivity trends.
 */
class ProductivityInsightsTool(context: Context) : Tool {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("productivity_insights", Context.MODE_PRIVATE)
    private val taskPrefs: SharedPreferences =
        context.getSharedPreferences("task_manager", Context.MODE_PRIVATE)
    private val teamPrefs: SharedPreferences =
        context.getSharedPreferences("team_manager", Context.MODE_PRIVATE)
    private val notePrefs: SharedPreferences =
        context.getSharedPreferences("smart_notes", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class DailyLog(
        val date: String,
        val focusHours: Float = 0f,
        val meetingHours: Float = 0f,
        val tasksCompleted: Int = 0,
        val tasksCreated: Int = 0,
        val notesCaptured: Int = 0,
        val mood: String = "",
        val energy: String = "",
        val keyWin: String = "",
    )

    override val name = "productivity_insights"
    override val description = "Track and analyze personal productivity. " +
            "Actions: 'log_day' (log daily focus/meeting hours and mood), " +
            "'weekly_report' (weekly productivity summary), " +
            "'trends' (productivity trends over time), " +
            "'task_stats' (task completion analytics from task_manager), " +
            "'delegation_ratio' (how much you delegate vs do yourself), " +
            "'suggestions' (AI-powered productivity suggestions based on patterns)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("log_day"); add("weekly_report"); add("trends")
                    add("task_stats"); add("delegation_ratio"); add("suggestions")
                })
            }
            putJsonObject("focus_hours") { put("type", "number"); put("description", "Hours of focused work today") }
            putJsonObject("meeting_hours") { put("type", "number"); put("description", "Hours in meetings today") }
            putJsonObject("mood") {
                put("type", "string")
                put("enum", buildJsonArray { add("great"); add("good"); add("okay"); add("low"); add("stressed") })
            }
            putJsonObject("energy") {
                put("type", "string")
                put("enum", buildJsonArray { add("high"); add("medium"); add("low") })
            }
            putJsonObject("key_win") { put("type", "string"); put("description", "Biggest win of the day") }
            putJsonObject("days") { put("type", "integer"); put("description", "Number of days to analyze (default 7)") }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "log_day" -> logDay(args)
                "weekly_report" -> weeklyReport(args)
                "trends" -> trends(args)
                "task_stats" -> taskAnalytics()
                "delegation_ratio" -> delegationRatio()
                "suggestions" -> suggestions()
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Productivity insights error: ${e.message}")
        }
    }

    private fun logDay(args: JsonObject): ToolResult {
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val log = DailyLog(
            date = dateKey,
            focusHours = args["focus_hours"]?.jsonPrimitive?.floatOrNull ?: 0f,
            meetingHours = args["meeting_hours"]?.jsonPrimitive?.floatOrNull ?: 0f,
            mood = args["mood"]?.jsonPrimitive?.contentOrNull ?: "",
            energy = args["energy"]?.jsonPrimitive?.contentOrNull ?: "",
            keyWin = args["key_win"]?.jsonPrimitive?.contentOrNull ?: "",
        )

        val logs = getAllLogs().toMutableMap()
        logs[dateKey] = log
        saveLogs(logs)

        val sb = StringBuilder("Daily log recorded for $dateKey:\n")
        if (log.focusHours > 0) sb.append("Focus time: ${log.focusHours}h\n")
        if (log.meetingHours > 0) sb.append("Meeting time: ${log.meetingHours}h\n")
        if (log.mood.isNotBlank()) sb.append("Mood: ${log.mood}\n")
        if (log.energy.isNotBlank()) sb.append("Energy: ${log.energy}\n")
        if (log.keyWin.isNotBlank()) sb.append("Key win: ${log.keyWin}\n")

        return ToolResult.success(sb.toString())
    }

    private fun weeklyReport(args: JsonObject): ToolResult {
        val days = args["days"]?.jsonPrimitive?.intOrNull ?: 7
        val logs = getRecentLogs(days)
        val tasks = getTaskData()
        val now = System.currentTimeMillis()
        val cutoff = now - days.toLong() * 24 * 60 * 60 * 1000

        val totalFocus = logs.values.sumOf { it.focusHours.toDouble() }
        val totalMeetings = logs.values.sumOf { it.meetingHours.toDouble() }
        val avgFocus = if (logs.isNotEmpty()) totalFocus / logs.size else 0.0
        val avgMeetings = if (logs.isNotEmpty()) totalMeetings / logs.size else 0.0

        val completedTasks = tasks.count { t ->
            t.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" &&
            (t.jsonObject["completedAt"]?.jsonPrimitive?.longOrNull ?: 0) > cutoff
        }
        val createdTasks = tasks.count { t ->
            (t.jsonObject["createdAt"]?.jsonPrimitive?.longOrNull ?: 0) > cutoff
        }

        val moods = logs.values.mapNotNull { it.mood.ifBlank { null } }
        val moodSummary = if (moods.isNotEmpty()) moods.groupBy { it }
            .maxByOrNull { it.value.size }?.key ?: "N/A" else "No mood data"

        val sb = StringBuilder("## Weekly Productivity Report (last $days days)\n\n")
        sb.append("**Time**\n")
        sb.append("Total focus: ${String.format("%.1f", totalFocus)}h (avg ${String.format("%.1f", avgFocus)}h/day)\n")
        sb.append("Total meetings: ${String.format("%.1f", totalMeetings)}h (avg ${String.format("%.1f", avgMeetings)}h/day)\n")
        val focusRatio = if (totalFocus + totalMeetings > 0)
            (totalFocus / (totalFocus + totalMeetings) * 100).toInt() else 0
        sb.append("Focus ratio: $focusRatio%\n\n")

        sb.append("**Tasks**\n")
        sb.append("Created: $createdTasks | Completed: $completedTasks\n")
        val throughput = if (createdTasks > 0) (completedTasks * 100 / createdTasks) else 0
        sb.append("Throughput: $throughput%\n\n")

        sb.append("**Wellbeing**\n")
        sb.append("Dominant mood: $moodSummary\n")

        val wins = logs.values.mapNotNull { it.keyWin.ifBlank { null } }
        if (wins.isNotEmpty()) {
            sb.append("\n**Key Wins**\n")
            wins.forEach { sb.append("- $it\n") }
        }

        return ToolResult.success(sb.toString())
    }

    private fun trends(args: JsonObject): ToolResult {
        val days = args["days"]?.jsonPrimitive?.intOrNull ?: 14
        val logs = getRecentLogs(days)

        if (logs.size < 2) return ToolResult.success("Need at least 2 days of data for trends. Use 'log_day' to record daily productivity.")

        val sortedLogs = logs.entries.sortedBy { it.key }
        val half = sortedLogs.size / 2
        val firstHalf = sortedLogs.take(half)
        val secondHalf = sortedLogs.drop(half)

        val avgFocusFirst = firstHalf.map { it.value.focusHours }.average()
        val avgFocusSecond = secondHalf.map { it.value.focusHours }.average()
        val focusTrend = if (avgFocusSecond > avgFocusFirst) "Improving" else if (avgFocusSecond < avgFocusFirst) "Declining" else "Stable"

        val avgMeetingsFirst = firstHalf.map { it.value.meetingHours }.average()
        val avgMeetingsSecond = secondHalf.map { it.value.meetingHours }.average()

        val sb = StringBuilder("## Productivity Trends (last $days days)\n\n")
        sb.append("Focus time trend: **$focusTrend**\n")
        sb.append("  First half avg: ${String.format("%.1f", avgFocusFirst)}h → Second half avg: ${String.format("%.1f", avgFocusSecond)}h\n\n")
        sb.append("Meeting load trend: ${String.format("%.1f", avgMeetingsFirst)}h → ${String.format("%.1f", avgMeetingsSecond)}h\n\n")

        sb.append("### Daily Breakdown\n")
        for ((date, log) in sortedLogs) {
            sb.append("$date: Focus ${log.focusHours}h | Meetings ${log.meetingHours}h")
            if (log.mood.isNotBlank()) sb.append(" | ${log.mood}")
            sb.append("\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun taskAnalytics(): ToolResult {
        val tasks = getTaskData()
        val now = System.currentTimeMillis()
        val weekAgo = now - 7L * 24 * 60 * 60 * 1000
        val monthAgo = now - 30L * 24 * 60 * 60 * 1000

        val total = tasks.size
        val done = tasks.count { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" }
        val pending = tasks.count { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "pending" }
        val high = tasks.count {
            it.jsonObject["priority"]?.jsonPrimitive?.contentOrNull == "high" &&
            it.jsonObject["status"]?.jsonPrimitive?.contentOrNull != "done"
        }
        val completedWeek = tasks.count {
            it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" &&
            (it.jsonObject["completedAt"]?.jsonPrimitive?.longOrNull ?: 0) > weekAgo
        }
        val completedMonth = tasks.count {
            it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" &&
            (it.jsonObject["completedAt"]?.jsonPrimitive?.longOrNull ?: 0) > monthAgo
        }

        val sb = StringBuilder("## Task Analytics\n\n")
        sb.append("Total tasks: $total | Done: $done | Pending: $pending\n")
        sb.append("Completion rate: ${if (total > 0) done * 100 / total else 0}%\n")
        sb.append("High priority (open): $high\n\n")
        sb.append("Completed this week: $completedWeek\n")
        sb.append("Completed this month: $completedMonth\n")
        sb.append("Weekly velocity: ${String.format("%.1f", completedWeek.toFloat())} tasks/week\n")

        return ToolResult.success(sb.toString())
    }

    private fun delegationRatio(): ToolResult {
        val tasks = getTaskData()
        val delegated = tasks.count {
            it.jsonObject["assignedTo"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true ||
            it.jsonObject["assigned_to"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true
        }
        val selfDone = tasks.size - delegated
        val ratio = if (tasks.isNotEmpty()) delegated * 100 / tasks.size else 0

        val delegations = getDelegationData()
        val activeDelegations = delegations.count {
            it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "assigned"
        }

        val sb = StringBuilder("## Delegation Analysis\n\n")
        sb.append("Total tasks: ${tasks.size}\n")
        sb.append("Self-managed: $selfDone | Delegated: $delegated\n")
        sb.append("Delegation ratio: $ratio%\n\n")
        sb.append("Active team delegations: $activeDelegations\n")

        if (ratio < 30) sb.append("\nSuggestion: Consider delegating more. A healthy delegation ratio for leaders is 40-60%.\n")
        else if (ratio > 70) sb.append("\nNote: High delegation ratio. Make sure you're staying connected to key deliverables.\n")
        else sb.append("\nYour delegation ratio is healthy!\n")

        return ToolResult.success(sb.toString())
    }

    private fun suggestions(): ToolResult {
        val logs = getRecentLogs(14)
        val tasks = getTaskData()
        val now = System.currentTimeMillis()

        val suggestions = mutableListOf<String>()

        // Analyze focus time
        val avgFocus = if (logs.isNotEmpty()) logs.values.map { it.focusHours }.average() else 0.0
        if (avgFocus < 3) suggestions.add("Your average focus time is ${String.format("%.1f", avgFocus)}h/day. Try blocking 3-4 hours of uninterrupted focus time each morning.")

        // Analyze meeting load
        val avgMeetings = if (logs.isNotEmpty()) logs.values.map { it.meetingHours }.average() else 0.0
        if (avgMeetings > 4) suggestions.add("You're averaging ${String.format("%.1f", avgMeetings)}h/day in meetings. Consider declining low-priority meetings or batching them into specific days.")

        // Analyze task overdue
        val overdue = tasks.count {
            it.jsonObject["status"]?.jsonPrimitive?.contentOrNull != "done" &&
            (it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE) < now
        }
        if (overdue > 3) suggestions.add("You have $overdue overdue tasks. Consider re-prioritizing or delegating some of them.")

        // Analyze high priority backlog
        val highPriority = tasks.count {
            it.jsonObject["priority"]?.jsonPrimitive?.contentOrNull == "high" &&
            it.jsonObject["status"]?.jsonPrimitive?.contentOrNull != "done"
        }
        if (highPriority > 5) suggestions.add("You have $highPriority high-priority tasks. Focus on completing 2-3 today before starting anything new.")

        // Mood analysis
        val moods = logs.values.mapNotNull { it.mood.ifBlank { null } }
        val lowMoodCount = moods.count { it == "low" || it == "stressed" }
        if (lowMoodCount > moods.size / 2 && moods.isNotEmpty()) {
            suggestions.add("You've reported low mood/stress frequently. Consider scheduling breaks, exercise, or reviewing your workload.")
        }

        if (suggestions.isEmpty()) {
            suggestions.add("Your productivity patterns look good! Keep up the great work.")
        }

        val sb = StringBuilder("## Productivity Suggestions\n\n")
        for (s in suggestions) {
            sb.append("- $s\n\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun getRecentLogs(days: Int): Map<String, DailyLog> {
        val all = getAllLogs()
        val cutoffDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -days)
        }
        val cutoffStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cutoffDate.time)
        return all.filter { it.key >= cutoffStr }
    }

    private fun getAllLogs(): Map<String, DailyLog> {
        val raw = prefs.getString("daily_logs", "{}") ?: "{}"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyMap() }
    }

    private fun saveLogs(logs: Map<String, DailyLog>) {
        prefs.edit().putString("daily_logs", json.encodeToString(logs)).apply()
    }

    private fun getTaskData(): List<JsonElement> {
        val raw = taskPrefs.getString("tasks_data", "[]") ?: "[]"
        return try { json.parseToJsonElement(raw).jsonArray.toList() } catch (_: Exception) { emptyList() }
    }

    private fun getDelegationData(): List<JsonElement> {
        val raw = teamPrefs.getString("delegations_data", "[]") ?: "[]"
        return try { json.parseToJsonElement(raw).jsonArray.toList() } catch (_: Exception) { emptyList() }
    }
}
