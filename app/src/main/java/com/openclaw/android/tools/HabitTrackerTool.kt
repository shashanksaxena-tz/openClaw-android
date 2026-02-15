package com.openclaw.android.tools

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HabitTrackerTool(context: Context) : Tool {

    private val prefs: SharedPreferences = context.getSharedPreferences("habit_tracker", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Habit(
        val name: String,
        val description: String = "",
        val frequency: String = "daily",       // daily, weekly
        val createdAt: Long = System.currentTimeMillis(),
        val completions: List<Long> = emptyList(), // timestamps of completions
        val streak: Int = 0,
    )

    override val name = "habit_tracker"
    override val description = "Track habits and routines. Create habits, log completions, view streaks and progress. " +
            "Actions: 'create' (new habit), 'complete' (log today's completion), 'list' (view all habits), " +
            "'stats' (view streaks and progress), 'delete' (remove a habit)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("create"); add("complete"); add("list"); add("stats"); add("delete") })
                put("description", "Habit tracker action")
            }
            putJsonObject("habit_name") {
                put("type", "string")
                put("description", "Name of the habit")
            }
            putJsonObject("description_text") {
                put("type", "string")
                put("description", "Description of the habit")
            }
            putJsonObject("frequency") {
                put("type", "string")
                put("description", "Tracking frequency: 'daily' or 'weekly' (default daily)")
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
                "create" -> {
                    val habitName = args["habit_name"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'habit_name'")
                    val desc = args["description_text"]?.jsonPrimitive?.contentOrNull ?: ""
                    val freq = args["frequency"]?.jsonPrimitive?.contentOrNull ?: "daily"
                    createHabit(habitName, desc, freq)
                }
                "complete" -> {
                    val habitName = args["habit_name"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'habit_name'")
                    completeHabit(habitName)
                }
                "list" -> listHabits()
                "stats" -> habitStats()
                "delete" -> {
                    val habitName = args["habit_name"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'habit_name'")
                    deleteHabit(habitName)
                }
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Habit tracker error: ${e.message}")
        }
    }

    private fun createHabit(name: String, description: String, frequency: String): ToolResult {
        val habits = getAllHabits().toMutableMap()
        if (habits.containsKey(name)) {
            return ToolResult.error("Habit '$name' already exists")
        }
        habits[name] = Habit(name = name, description = description, frequency = frequency)
        saveAll(habits)
        return ToolResult.success("Created habit: **$name** (tracked $frequency)")
    }

    private fun completeHabit(name: String): ToolResult {
        val habits = getAllHabits().toMutableMap()
        val habit = habits[name] ?: return ToolResult.error("Habit '$name' not found")

        val now = System.currentTimeMillis()
        val todayStart = now - (now % (24 * 60 * 60 * 1000))

        // Check if already completed today
        val alreadyDone = habit.completions.any { it >= todayStart }
        if (alreadyDone) {
            return ToolResult.success("Already completed '$name' today! Current streak: ${habit.streak} days")
        }

        val updatedCompletions = habit.completions + now
        val newStreak = calculateStreak(updatedCompletions, habit.frequency)
        habits[name] = habit.copy(completions = updatedCompletions, streak = newStreak)
        saveAll(habits)

        return ToolResult.success("Completed **$name**! Streak: $newStreak ${habit.frequency} completions in a row")
    }

    private fun listHabits(): ToolResult {
        val habits = getAllHabits()
        if (habits.isEmpty()) return ToolResult.success("No habits tracked yet. Create one with 'create' action.")

        val sb = StringBuilder("Your habits (${habits.size}):\n\n")
        val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000))

        habits.values.sortedByDescending { it.streak }.forEach { h ->
            val doneToday = h.completions.any { it >= todayStart }
            val status = if (doneToday) "Done" else "Pending"
            sb.append("- **${h.name}** [$status] — Streak: ${h.streak} | Total: ${h.completions.size} completions\n")
            if (h.description.isNotBlank()) sb.append("  ${h.description}\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun habitStats(): ToolResult {
        val habits = getAllHabits()
        if (habits.isEmpty()) return ToolResult.success("No habits tracked yet.")

        val sb = StringBuilder("Habit Statistics:\n\n")
        val now = System.currentTimeMillis()
        val todayStart = now - (now % (24 * 60 * 60 * 1000))
        val weekStart = todayStart - 6 * 24 * 60 * 60 * 1000

        var totalCompleted = 0
        var totalPending = 0

        habits.values.sortedByDescending { it.streak }.forEach { h ->
            val doneToday = h.completions.any { it >= todayStart }
            if (doneToday) totalCompleted++ else totalPending++

            val thisWeek = h.completions.count { it >= weekStart }
            val dateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
            val lastDone = if (h.completions.isNotEmpty()) dateFormat.format(Date(h.completions.last())) else "never"

            sb.append("**${h.name}**:\n")
            sb.append("  Streak: ${h.streak} | This week: $thisWeek/${if (h.frequency == "daily") 7 else 1}\n")
            sb.append("  Total completions: ${h.completions.size} | Last: $lastDone\n\n")
        }

        sb.append("---\n")
        sb.append("Today: $totalCompleted done, $totalPending pending\n")
        sb.append("Overall completion rate: ${if (habits.isNotEmpty()) "${"%.0f".format(totalCompleted * 100.0 / habits.size)}%" else "N/A"}")

        return ToolResult.success(sb.toString())
    }

    private fun deleteHabit(name: String): ToolResult {
        val habits = getAllHabits().toMutableMap()
        if (habits.remove(name) == null) return ToolResult.error("Habit '$name' not found")
        saveAll(habits)
        return ToolResult.success("Deleted habit: $name")
    }

    private fun calculateStreak(completions: List<Long>, frequency: String): Int {
        if (completions.isEmpty()) return 0
        val sorted = completions.sorted()
        val interval = if (frequency == "weekly") 7L * 24 * 60 * 60 * 1000 else 24L * 60 * 60 * 1000
        val tolerance = interval + interval / 2 // 50% grace period

        var streak = 1
        for (i in sorted.size - 1 downTo 1) {
            val gap = sorted[i] - sorted[i - 1]
            if (gap <= tolerance) streak++ else break
        }
        return streak
    }

    private fun getAllHabits(): Map<String, Habit> {
        val raw = prefs.getString("habits_data", "{}") ?: "{}"
        return try {
            json.decodeFromString<Map<String, Habit>>(raw)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveAll(habits: Map<String, Habit>) {
        prefs.edit().putString("habits_data", json.encodeToString(habits)).apply()
    }
}
