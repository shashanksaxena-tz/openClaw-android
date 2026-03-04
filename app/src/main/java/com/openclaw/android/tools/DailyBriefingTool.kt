package com.openclaw.android.tools

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.provider.CalendarContract
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Daily Briefing / AI Prioritization & Planning tool.
 * Generates daily agenda, recommended priorities, workload balance insights,
 * and time blocking suggestions by combining calendar, tasks, notes, and team data.
 */
class DailyBriefingTool(private val context: Context) : Tool {

    private val taskPrefs: SharedPreferences =
        context.getSharedPreferences("task_manager", Context.MODE_PRIVATE)
    private val notePrefs: SharedPreferences =
        context.getSharedPreferences("smart_notes", Context.MODE_PRIVATE)
    private val teamPrefs: SharedPreferences =
        context.getSharedPreferences("team_manager", Context.MODE_PRIVATE)
    private val habitPrefs: SharedPreferences =
        context.getSharedPreferences("habit_tracker", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    override val name = "daily_briefing"
    override val description = "Generate a daily briefing with today's agenda, priorities, " +
            "calendar events, pending tasks, team check-ins, and suggested time blocks. " +
            "Actions: 'briefing' (full daily briefing), 'priorities' (top priorities), " +
            "'time_blocks' (suggested schedule), 'end_of_day' (end-of-day summary)."

    override val requiredPermissions = listOf(Manifest.permission.READ_CALENDAR)

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("briefing"); add("priorities"); add("time_blocks"); add("end_of_day")
                })
                put("description", "Briefing type")
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
                "briefing" -> fullBriefing()
                "priorities" -> topPriorities()
                "time_blocks" -> suggestTimeBlocks()
                "end_of_day" -> endOfDaySummary()
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Briefing error: ${e.message}")
        }
    }

    private fun fullBriefing(): ToolResult {
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val now = System.currentTimeMillis()
        val today = dateFormat.format(Date(now))

        val sb = StringBuilder("## Good ${getTimeOfDay()}! Daily Briefing\n")
        sb.append("$today\n\n")

        // Calendar events today
        val events = getTodayCalendarEvents()
        sb.append("### Calendar (${events.size} events)\n")
        if (events.isEmpty()) {
            sb.append("No meetings scheduled today — great for focus work!\n")
        } else {
            for (event in events) {
                sb.append("- ${timeFormat.format(Date(event.first))} — ${event.second}\n")
            }
        }
        sb.append("\n")

        // High-priority tasks
        val tasks = getActiveTasks()
        val highTasks = tasks.filter {
            it.jsonObject["priority"]?.jsonPrimitive?.contentOrNull == "high"
        }
        val overdue = tasks.filter {
            val deadline = it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull
            deadline != null && deadline < now
        }

        sb.append("### Tasks Overview\n")
        sb.append("Total pending: ${tasks.size} | High priority: ${highTasks.size} | Overdue: ${overdue.size}\n\n")

        if (highTasks.isNotEmpty()) {
            sb.append("**Top priorities:**\n")
            for (t in highTasks.take(5)) {
                val title = t.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                val project = t.jsonObject["project"]?.jsonPrimitive?.contentOrNull ?: ""
                sb.append("  !!! $title [$project]\n")
            }
            sb.append("\n")
        }

        if (overdue.isNotEmpty()) {
            sb.append("**Overdue:**\n")
            for (t in overdue.take(3)) {
                val title = t.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                sb.append("  !!! $title\n")
            }
            sb.append("\n")
        }

        // Team check-ins
        val teamCheckIns = getTeamCheckInsDue()
        if (teamCheckIns.isNotEmpty()) {
            sb.append("### Team Check-ins Due\n")
            for (name in teamCheckIns) {
                sb.append("- $name\n")
            }
            sb.append("\n")
        }

        // Overdue delegations
        val overdueDelegations = getOverdueDelegations()
        if (overdueDelegations.isNotEmpty()) {
            sb.append("### Overdue Delegations\n")
            for ((person, task) in overdueDelegations) {
                sb.append("- $person: $task\n")
            }
            sb.append("\n")
        }

        // Motivation
        val totalCompleted = getTotalCompletedTasks()
        if (totalCompleted > 0) {
            sb.append("---\n")
            sb.append("You've completed $totalCompleted tasks total. Keep up the momentum!\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun topPriorities(): ToolResult {
        val tasks = getActiveTasks()
        val sorted = tasks.sortedWith(compareBy<JsonElement> {
            when (it.jsonObject["priority"]?.jsonPrimitive?.contentOrNull) {
                "high" -> 0; "medium" -> 1; else -> 2
            }
        }.thenBy {
            it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
        })

        val sb = StringBuilder("## Today's Priorities\n\n")
        for ((i, t) in sorted.take(10).withIndex()) {
            val title = t.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
            val priority = t.jsonObject["priority"]?.jsonPrimitive?.contentOrNull ?: "medium"
            val project = t.jsonObject["project"]?.jsonPrimitive?.contentOrNull ?: ""
            sb.append("${i + 1}. [$priority] **$title** [$project]\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun suggestTimeBlocks(): ToolResult {
        val events = getTodayCalendarEvents()
        val tasks = getActiveTasks()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        val sb = StringBuilder("## Suggested Time Blocks for Today\n\n")

        // Simple time block suggestions based on events
        val morningStart = getTimeMillis(9, 0)
        val morningEnd = getTimeMillis(12, 0)
        val afternoonStart = getTimeMillis(13, 0)
        val afternoonEnd = getTimeMillis(17, 0)

        val morningEvents = events.filter { it.first in morningStart..morningEnd }
        val afternoonEvents = events.filter { it.first in afternoonStart..afternoonEnd }

        val highTasks = tasks.filter {
            it.jsonObject["priority"]?.jsonPrimitive?.contentOrNull == "high"
        }

        sb.append("### Morning (9 AM - 12 PM)\n")
        if (morningEvents.isEmpty() && highTasks.isNotEmpty()) {
            sb.append("- **9:00 - 11:00** Deep focus block (tackle high-priority tasks)\n")
            sb.append("- **11:00 - 12:00** Communication catch-up (emails, messages)\n")
        } else {
            for (e in morningEvents) {
                sb.append("- **${timeFormat.format(Date(e.first))}** ${e.second}\n")
            }
            sb.append("- Use gaps for focused task work\n")
        }
        sb.append("\n")

        sb.append("### Afternoon (1 PM - 5 PM)\n")
        if (afternoonEvents.isEmpty()) {
            sb.append("- **1:00 - 3:00** Project work / medium-priority tasks\n")
            sb.append("- **3:00 - 4:00** Team check-ins\n")
            sb.append("- **4:00 - 5:00** Planning and review\n")
        } else {
            for (e in afternoonEvents) {
                sb.append("- **${timeFormat.format(Date(e.first))}** ${e.second}\n")
            }
            sb.append("- Use gaps for task completion\n")
        }
        sb.append("\n")

        sb.append("### End of Day\n")
        sb.append("- **5:00 - 5:15** Review what you accomplished\n")
        sb.append("- **5:15 - 5:30** Plan tomorrow's priorities\n")

        return ToolResult.success(sb.toString())
    }

    private fun endOfDaySummary(): ToolResult {
        val now = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis

        val tasks = getTaskDataRaw()
        val completedToday = tasks.count {
            it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" &&
            (it.jsonObject["completedAt"]?.jsonPrimitive?.longOrNull ?: 0) > todayStart
        }
        val createdToday = tasks.count {
            (it.jsonObject["createdAt"]?.jsonPrimitive?.longOrNull ?: 0) > todayStart
        }

        val notes = getNoteDataRaw()
        val notesToday = notes.count {
            (it.jsonObject["createdAt"]?.jsonPrimitive?.longOrNull ?: 0) > todayStart
        }

        val events = getTodayCalendarEvents()

        val sb = StringBuilder("## End of Day Summary\n\n")
        sb.append("Meetings attended: ${events.size}\n")
        sb.append("Tasks completed: $completedToday\n")
        sb.append("Tasks created: $createdToday\n")
        sb.append("Notes captured: $notesToday\n\n")

        val remaining = tasks.count {
            it.jsonObject["status"]?.jsonPrimitive?.contentOrNull != "done" &&
            it.jsonObject["priority"]?.jsonPrimitive?.contentOrNull == "high"
        }
        if (remaining > 0) {
            sb.append("High-priority tasks remaining: $remaining\n")
            sb.append("Consider carrying these over to tomorrow's top priorities.\n\n")
        }

        if (completedToday > 0) {
            sb.append("Great job completing $completedToday tasks today!\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun getTodayCalendarEvents(): List<Pair<Long, String>> {
        return try {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            val todayStart = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val todayEnd = cal.timeInMillis

            val projection = arrayOf(
                CalendarContract.Events.DTSTART,
                CalendarContract.Events.TITLE,
            )
            val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
            val selectionArgs = arrayOf(todayStart.toString(), todayEnd.toString())

            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection, selection, selectionArgs,
                "${CalendarContract.Events.DTSTART} ASC"
            ) ?: return emptyList()

            val events = mutableListOf<Pair<Long, String>>()
            cursor.use {
                while (it.moveToNext()) {
                    val start = it.getLong(0)
                    val title = it.getString(1) ?: "Untitled"
                    events.add(start to title)
                }
            }
            events
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun getActiveTasks(): List<JsonElement> {
        val raw = taskPrefs.getString("tasks_data", "[]") ?: "[]"
        return try {
            json.parseToJsonElement(raw).jsonArray.filter {
                it.jsonObject["status"]?.jsonPrimitive?.contentOrNull != "done"
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun getTaskDataRaw(): List<JsonElement> {
        val raw = taskPrefs.getString("tasks_data", "[]") ?: "[]"
        return try { json.parseToJsonElement(raw).jsonArray.toList() } catch (_: Exception) { emptyList() }
    }

    private fun getNoteDataRaw(): List<JsonElement> {
        val raw = notePrefs.getString("notes_data", "[]") ?: "[]"
        return try { json.parseToJsonElement(raw).jsonArray.toList() } catch (_: Exception) { emptyList() }
    }

    private fun getTeamCheckInsDue(): List<String> {
        val membersRaw = teamPrefs.getString("members_data", "[]") ?: "[]"
        val notesRaw = teamPrefs.getString("notes_data", "[]") ?: "[]"
        val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000

        return try {
            val members = json.parseToJsonElement(membersRaw).jsonArray
            val notes = json.parseToJsonElement(notesRaw).jsonArray

            members.filter { m ->
                val memberId = m.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
                val lastNote = notes.filter {
                    it.jsonObject["memberId"]?.jsonPrimitive?.contentOrNull == memberId
                }.maxByOrNull {
                    it.jsonObject["date"]?.jsonPrimitive?.longOrNull ?: 0
                }
                lastNote == null || (lastNote.jsonObject["date"]?.jsonPrimitive?.longOrNull ?: 0) < weekAgo
            }.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
        } catch (_: Exception) { emptyList() }
    }

    private fun getOverdueDelegations(): List<Pair<String, String>> {
        val delegationsRaw = teamPrefs.getString("delegations_data", "[]") ?: "[]"
        val membersRaw = teamPrefs.getString("members_data", "[]") ?: "[]"
        val now = System.currentTimeMillis()

        return try {
            val delegations = json.parseToJsonElement(delegationsRaw).jsonArray
            val members = json.parseToJsonElement(membersRaw).jsonArray

            delegations.filter { d ->
                d.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "assigned" &&
                (d.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE) < now
            }.map { d ->
                val memberId = d.jsonObject["memberId"]?.jsonPrimitive?.contentOrNull ?: ""
                val memberName = members.find {
                    it.jsonObject["id"]?.jsonPrimitive?.contentOrNull == memberId
                }?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown"
                val task = d.jsonObject["task"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                memberName to task
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun getTotalCompletedTasks(): Int {
        val raw = taskPrefs.getString("tasks_data", "[]") ?: "[]"
        return try {
            json.parseToJsonElement(raw).jsonArray.count {
                it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done"
            }
        } catch (_: Exception) { 0 }
    }

    private fun getTimeOfDay(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour < 12 -> "morning"
            hour < 17 -> "afternoon"
            else -> "evening"
        }
    }

    private fun getTimeMillis(hour: Int, minute: Int): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }.timeInMillis
    }
}
