package com.openclaw.android.tools

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Smart Note Capture & Categorization tool.
 * Captures voice/text notes and organizes them by category and date.
 * Categories: travel, meeting, task, business_idea, email_reply,
 * personal, reminder, decision, team_note
 */
class SmartNoteTool(context: Context) : Tool {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("smart_notes", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Note(
        val id: String,
        val content: String,
        val category: String,
        val createdAt: Long,
        val eventDate: Long? = null,
        val deadlineDate: Long? = null,
        val tags: List<String> = emptyList(),
        val linkedEventId: String? = null,
        val priority: String = "normal",
    )

    override val name = "smart_notes"
    override val description = "Capture, categorize, search, and organize notes. " +
            "Supports categories: travel, meeting, task, business_idea, email_reply, " +
            "personal, reminder, decision, team_note. " +
            "Actions: 'create' (capture a new note), 'list' (list notes by category/date), " +
            "'search' (full-text search), 'timeline' (date-organized view), " +
            "'delete' (remove a note), 'update' (modify a note)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("create"); add("list"); add("search")
                    add("timeline"); add("delete"); add("update")
                })
                put("description", "Note action to perform")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "Note content text")
            }
            putJsonObject("category") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("travel"); add("meeting"); add("task")
                    add("business_idea"); add("email_reply"); add("personal")
                    add("reminder"); add("decision"); add("team_note")
                })
                put("description", "Note category")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query")
            }
            putJsonObject("note_id") {
                put("type", "string")
                put("description", "Note ID for update/delete")
            }
            putJsonObject("event_date") {
                put("type", "string")
                put("description", "Related event date (ISO 8601: 2024-03-15)")
            }
            putJsonObject("deadline") {
                put("type", "string")
                put("description", "Deadline date (ISO 8601: 2024-03-15)")
            }
            putJsonObject("tags") {
                put("type", "string")
                put("description", "Comma-separated tags")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("enum", buildJsonArray { add("high"); add("normal"); add("low") })
                put("description", "Note priority level")
            }
            putJsonObject("days") {
                put("type", "integer")
                put("description", "Number of days for timeline view (default 7)")
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
                "create" -> createNote(args)
                "list" -> listNotes(args)
                "search" -> searchNotes(args)
                "timeline" -> timelineView(args)
                "delete" -> deleteNote(args)
                "update" -> updateNote(args)
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Smart notes error: ${e.message}")
        }
    }

    private fun createNote(args: JsonObject): ToolResult {
        val content = args["content"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'content'")
        val category = args["category"]?.jsonPrimitive?.contentOrNull ?: "personal"
        val priority = args["priority"]?.jsonPrimitive?.contentOrNull ?: "normal"
        val tags = args["tags"]?.jsonPrimitive?.contentOrNull
            ?.split(",")?.map { it.trim() } ?: emptyList()

        val eventDate = args["event_date"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) }
        val deadline = args["deadline"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) }

        val id = UUID.randomUUID().toString().take(8)
        val note = Note(
            id = id,
            content = content,
            category = category,
            createdAt = System.currentTimeMillis(),
            eventDate = eventDate,
            deadlineDate = deadline,
            tags = tags,
            priority = priority,
        )

        val notes = getAllNotes().toMutableList()
        notes.add(note)
        saveAllNotes(notes)

        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return ToolResult.success(
            "Note captured (ID: $id)\n" +
            "Category: $category | Priority: $priority\n" +
            "Created: ${dateFormat.format(Date(note.createdAt))}\n" +
            if (tags.isNotEmpty()) "Tags: ${tags.joinToString(", ")}\n" else ""
        )
    }

    private fun listNotes(args: JsonObject): ToolResult {
        val category = args["category"]?.jsonPrimitive?.contentOrNull
        val notes = getAllNotes()
            .let { if (category != null) it.filter { n -> n.category == category } else it }
            .sortedByDescending { it.createdAt }

        if (notes.isEmpty()) {
            return ToolResult.success(
                if (category != null) "No notes in category '$category'."
                else "No notes captured yet."
            )
        }

        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("Notes${if (category != null) " [$category]" else ""} (${notes.size} total):\n\n")

        for (note in notes.take(30)) {
            val priorityIcon = when (note.priority) {
                "high" -> "!!!"
                "low" -> " - "
                else -> " * "
            }
            sb.append("$priorityIcon **${note.category}** (ID: ${note.id})\n")
            sb.append("  ${note.content.take(120)}${if (note.content.length > 120) "..." else ""}\n")
            sb.append("  ${dateFormat.format(Date(note.createdAt))}")
            if (note.tags.isNotEmpty()) sb.append(" | Tags: ${note.tags.joinToString(", ")}")
            sb.append("\n\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun searchNotes(args: JsonObject): ToolResult {
        val query = args["query"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'query'")

        val queryLower = query.lowercase()
        val notes = getAllNotes().filter { note ->
            note.content.lowercase().contains(queryLower) ||
            note.category.lowercase().contains(queryLower) ||
            note.tags.any { it.lowercase().contains(queryLower) }
        }.sortedByDescending { it.createdAt }

        if (notes.isEmpty()) {
            return ToolResult.success("No notes matching '$query'.")
        }

        val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        val sb = StringBuilder("Found ${notes.size} notes matching '$query':\n\n")

        for (note in notes.take(20)) {
            sb.append("- [${note.category}] (ID: ${note.id}) ${note.content.take(100)}\n")
            sb.append("  ${dateFormat.format(Date(note.createdAt))}\n\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun timelineView(args: JsonObject): ToolResult {
        val days = args["days"]?.jsonPrimitive?.intOrNull ?: 7
        val now = System.currentTimeMillis()
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        val endRange = startOfToday + days.toLong() * 24 * 60 * 60 * 1000

        val notes = getAllNotes()
        val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        // Group by date
        val grouped = mutableMapOf<String, MutableList<Note>>()

        for (note in notes) {
            val relevantDate = note.eventDate ?: note.deadlineDate ?: note.createdAt
            if (relevantDate in startOfToday..endRange) {
                val dayKey = dateFormat.format(Date(relevantDate))
                grouped.getOrPut(dayKey) { mutableListOf() }.add(note)
            }
        }

        if (grouped.isEmpty()) {
            return ToolResult.success("No notes in the next $days days.")
        }

        val sb = StringBuilder("Timeline (next $days days):\n\n")
        for ((day, dayNotes) in grouped) {
            sb.append("### $day\n")
            for (note in dayNotes.sortedBy { it.eventDate ?: it.createdAt }) {
                val time = timeFormat.format(Date(note.eventDate ?: note.createdAt))
                sb.append("  $time - [${note.category}] ${note.content.take(80)}\n")
            }
            sb.append("\n")
        }

        return ToolResult.success(sb.toString())
    }

    private fun deleteNote(args: JsonObject): ToolResult {
        val noteId = args["note_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'note_id'")

        val notes = getAllNotes().toMutableList()
        val removed = notes.removeAll { it.id == noteId }
        if (!removed) return ToolResult.error("Note '$noteId' not found.")

        saveAllNotes(notes)
        return ToolResult.success("Note '$noteId' deleted.")
    }

    private fun updateNote(args: JsonObject): ToolResult {
        val noteId = args["note_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'note_id'")

        val notes = getAllNotes().toMutableList()
        val index = notes.indexOfFirst { it.id == noteId }
        if (index == -1) return ToolResult.error("Note '$noteId' not found.")

        val existing = notes[index]
        val updated = existing.copy(
            content = args["content"]?.jsonPrimitive?.contentOrNull ?: existing.content,
            category = args["category"]?.jsonPrimitive?.contentOrNull ?: existing.category,
            priority = args["priority"]?.jsonPrimitive?.contentOrNull ?: existing.priority,
            tags = args["tags"]?.jsonPrimitive?.contentOrNull
                ?.split(",")?.map { it.trim() } ?: existing.tags,
            eventDate = args["event_date"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) }
                ?: existing.eventDate,
            deadlineDate = args["deadline"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) }
                ?: existing.deadlineDate,
        )

        notes[index] = updated
        saveAllNotes(notes)
        return ToolResult.success("Note '$noteId' updated.")
    }

    private fun parseDate(dateStr: String): Long? {
        val formats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()),
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
        )
        for (fmt in formats) {
            try { return fmt.parse(dateStr)?.time } catch (_: Exception) {}
        }
        return null
    }

    private fun getAllNotes(): List<Note> {
        val raw = prefs.getString("notes_data", "[]") ?: "[]"
        return try {
            json.decodeFromString<List<Note>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAllNotes(notes: List<Note>) {
        prefs.edit().putString("notes_data", json.encodeToString(notes)).apply()
    }
}
