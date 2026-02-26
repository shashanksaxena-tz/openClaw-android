package com.openclaw.android.tools

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Decision Log & Growth Tracking tool.
 * Records decisions, reflections, lessons learned, and leadership journal entries.
 * Enables personal growth tracking and reviewing past decisions.
 */
class DecisionLogTool(context: Context) : Tool {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("decision_log", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class DecisionEntry(
        val id: String,
        val type: String,
        val title: String,
        val content: String,
        val outcome: String = "",
        val lessonsLearned: String = "",
        val tags: List<String> = emptyList(),
        val createdAt: Long,
        val reviewDate: Long? = null,
    )

    override val name = "decision_log"
    override val description = "Record decisions, reflections, lessons learned, and journal entries for personal growth. " +
            "Actions: 'log' (record a new entry), 'list' (list entries by type), " +
            "'search' (search entries), 'review' (entries due for review), " +
            "'add_outcome' (add outcome to past decision), " +
            "'growth_report' (personal growth summary), 'delete' (remove entry)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("log"); add("list"); add("search")
                    add("review"); add("add_outcome")
                    add("growth_report"); add("delete")
                })
            }
            putJsonObject("type") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("decision"); add("reflection"); add("lesson")
                    add("journal"); add("what_worked"); add("what_didnt")
                })
                put("description", "Entry type")
            }
            putJsonObject("title") { put("type", "string"); put("description", "Entry title") }
            putJsonObject("content") { put("type", "string"); put("description", "Entry content/details") }
            putJsonObject("outcome") { put("type", "string"); put("description", "Decision outcome") }
            putJsonObject("lessons_learned") { put("type", "string"); put("description", "Lessons from this experience") }
            putJsonObject("tags") { put("type", "string"); put("description", "Comma-separated tags") }
            putJsonObject("entry_id") { put("type", "string"); put("description", "Entry ID for update/delete") }
            putJsonObject("review_in_days") { put("type", "integer"); put("description", "Set review reminder in N days") }
            putJsonObject("query") { put("type", "string"); put("description", "Search query") }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "log" -> logEntry(args)
                "list" -> listEntries(args)
                "search" -> searchEntries(args)
                "review" -> reviewDue()
                "add_outcome" -> addOutcome(args)
                "growth_report" -> growthReport()
                "delete" -> deleteEntry(args)
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Decision log error: ${e.message}")
        }
    }

    private fun logEntry(args: JsonObject): ToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: "journal"
        val title = args["title"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'title'")
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'content'")

        val reviewDays = args["review_in_days"]?.jsonPrimitive?.intOrNull
        val reviewDate = if (reviewDays != null)
            System.currentTimeMillis() + reviewDays.toLong() * 24 * 60 * 60 * 1000 else null

        val id = UUID.randomUUID().toString().take(8)
        val entry = DecisionEntry(
            id = id, type = type, title = title, content = content,
            outcome = args["outcome"]?.jsonPrimitive?.contentOrNull ?: "",
            lessonsLearned = args["lessons_learned"]?.jsonPrimitive?.contentOrNull ?: "",
            tags = args["tags"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: emptyList(),
            createdAt = System.currentTimeMillis(),
            reviewDate = reviewDate,
        )

        val entries = getAllEntries().toMutableList()
        entries.add(entry)
        saveAllEntries(entries)

        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val sb = StringBuilder("Entry logged (ID: $id)\n")
        sb.append("Type: $type | Title: $title\n")
        if (reviewDate != null) sb.append("Review scheduled: ${df.format(Date(reviewDate))}\n")

        return ToolResult.success(sb.toString())
    }

    private fun listEntries(args: JsonObject): ToolResult {
        val type = args["type"]?.jsonPrimitive?.contentOrNull

        var entries = getAllEntries()
        if (type != null) entries = entries.filter { it.type == type }
        entries = entries.sortedByDescending { it.createdAt }

        if (entries.isEmpty()) {
            return ToolResult.success(
                if (type != null) "No $type entries yet." else "No entries recorded yet."
            )
        }

        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val sb = StringBuilder("${type?.replaceFirstChar { it.uppercase() } ?: "All"} Entries (${entries.size}):\n\n")

        for (e in entries.take(20)) {
            sb.append("**[${e.type}] ${e.title}** (ID: ${e.id})\n")
            sb.append("  ${e.content.take(100)}${if (e.content.length > 100) "..." else ""}\n")
            if (e.outcome.isNotBlank()) sb.append("  Outcome: ${e.outcome.take(60)}\n")
            if (e.lessonsLearned.isNotBlank()) sb.append("  Lesson: ${e.lessonsLearned.take(60)}\n")
            sb.append("  ${df.format(Date(e.createdAt))}\n\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun searchEntries(args: JsonObject): ToolResult {
        val query = args["query"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'query'")

        val queryLower = query.lowercase()
        val entries = getAllEntries().filter {
            it.title.lowercase().contains(queryLower) ||
            it.content.lowercase().contains(queryLower) ||
            it.outcome.lowercase().contains(queryLower) ||
            it.lessonsLearned.lowercase().contains(queryLower) ||
            it.tags.any { t -> t.lowercase().contains(queryLower) }
        }.sortedByDescending { it.createdAt }

        if (entries.isEmpty()) return ToolResult.success("No entries matching '$query'.")

        val df = SimpleDateFormat("MMM d", Locale.getDefault())
        val sb = StringBuilder("Found ${entries.size} entries matching '$query':\n\n")
        for (e in entries.take(15)) {
            sb.append("- [${e.type}] **${e.title}** (${df.format(Date(e.createdAt))})\n")
            sb.append("  ${e.content.take(80)}\n\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun reviewDue(): ToolResult {
        val now = System.currentTimeMillis()
        val entries = getAllEntries().filter {
            it.reviewDate != null && it.reviewDate <= now
        }.sortedBy { it.reviewDate }

        if (entries.isEmpty()) return ToolResult.success("No entries due for review.")

        val df = SimpleDateFormat("MMM d", Locale.getDefault())
        val sb = StringBuilder("## Entries Due for Review (${entries.size})\n\n")
        for (e in entries) {
            sb.append("**[${e.type}] ${e.title}** (ID: ${e.id})\n")
            sb.append("  ${e.content.take(100)}\n")
            if (e.outcome.isNotBlank()) sb.append("  Outcome: ${e.outcome}\n")
            sb.append("  Logged: ${df.format(Date(e.createdAt))}\n\n")
            sb.append("  Consider: What happened since? Was this the right call?\n\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun addOutcome(args: JsonObject): ToolResult {
        val entryId = args["entry_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'entry_id'")

        val entries = getAllEntries().toMutableList()
        val index = entries.indexOfFirst { it.id == entryId }
        if (index == -1) return ToolResult.error("Entry '$entryId' not found.")

        val e = entries[index]
        entries[index] = e.copy(
            outcome = args["outcome"]?.jsonPrimitive?.contentOrNull ?: e.outcome,
            lessonsLearned = args["lessons_learned"]?.jsonPrimitive?.contentOrNull ?: e.lessonsLearned,
        )
        saveAllEntries(entries)
        return ToolResult.success("Outcome added to '${e.title}'.")
    }

    private fun growthReport(): ToolResult {
        val entries = getAllEntries()
        val now = System.currentTimeMillis()
        val monthAgo = now - 30L * 24 * 60 * 60 * 1000

        val total = entries.size
        val thisMonth = entries.count { it.createdAt > monthAgo }
        val byType = entries.groupBy { it.type }.mapValues { it.value.size }
        val withLessons = entries.count { it.lessonsLearned.isNotBlank() }
        val withOutcomes = entries.count { it.outcome.isNotBlank() }

        val sb = StringBuilder("## Personal Growth Report\n\n")
        sb.append("Total entries: $total | This month: $thisMonth\n")
        sb.append("With outcomes recorded: $withOutcomes | With lessons: $withLessons\n\n")

        sb.append("**By Type:**\n")
        for ((type, count) in byType.toList().sortedByDescending { it.second }) {
            sb.append("  $type: $count\n")
        }
        sb.append("\n")

        // Recent lessons
        val recentLessons = entries.filter { it.lessonsLearned.isNotBlank() }
            .sortedByDescending { it.createdAt }.take(5)
        if (recentLessons.isNotEmpty()) {
            sb.append("**Recent Lessons Learned:**\n")
            for (e in recentLessons) {
                sb.append("- ${e.lessonsLearned.take(80)} (from: ${e.title})\n")
            }
            sb.append("\n")
        }

        // Things that worked
        val wins = entries.filter { it.type == "what_worked" }.sortedByDescending { it.createdAt }.take(3)
        if (wins.isNotEmpty()) {
            sb.append("**Recent Wins:**\n")
            for (w in wins) {
                sb.append("- ${w.title}: ${w.content.take(60)}\n")
            }
        }

        return ToolResult.success(sb.toString())
    }

    private fun deleteEntry(args: JsonObject): ToolResult {
        val entryId = args["entry_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'entry_id'")

        val entries = getAllEntries().toMutableList()
        val removed = entries.removeAll { it.id == entryId }
        if (!removed) return ToolResult.error("Entry '$entryId' not found.")

        saveAllEntries(entries)
        return ToolResult.success("Entry '$entryId' deleted.")
    }

    private fun getAllEntries(): List<DecisionEntry> {
        val raw = prefs.getString("entries_data", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    private fun saveAllEntries(entries: List<DecisionEntry>) {
        prefs.edit().putString("entries_data", json.encodeToString(entries)).apply()
    }
}
