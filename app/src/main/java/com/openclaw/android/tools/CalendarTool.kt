package com.openclaw.android.tools

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class CalendarTool(private val context: Context) : Tool {

    override val name = "calendar"

    override val requiredPermissions = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )
    override val description = "Read upcoming calendar events, search events, create or delete events. " +
            "Actions: 'list' (list upcoming events), 'search' (search events by query), " +
            "'create' (create a new event), 'delete' (delete an event by ID)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray { add("list"); add("search"); add("create"); add("delete") })
                put("description", "Action to perform")
            }
            putJsonObject("event_id") {
                put("type", "string")
                put("description", "Event ID (for delete action — get IDs from list/search results)")
            }
            putJsonObject("days") {
                put("type", "integer")
                put("description", "Number of days ahead to list events (default 7)")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Search query for event titles")
            }
            putJsonObject("title") {
                put("type", "string")
                put("description", "Event title (for create)")
            }
            putJsonObject("description_text") {
                put("type", "string")
                put("description", "Event description (for create)")
            }
            putJsonObject("start_time") {
                put("type", "string")
                put("description", "Start time in ISO 8601 format, e.g. 2024-03-15T14:00:00 (for create)")
            }
            putJsonObject("end_time") {
                put("type", "string")
                put("description", "End time in ISO 8601 format (for create)")
            }
            putJsonObject("location") {
                put("type", "string")
                put("description", "Event location (for create)")
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
                "list" -> listEvents(args["days"]?.jsonPrimitive?.intOrNull ?: 7)
                "search" -> {
                    val query = args["query"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'query' for search")
                    searchEvents(query)
                }
                "create" -> createEvent(args)
                "delete" -> {
                    val eventId = args["event_id"]?.jsonPrimitive?.contentOrNull
                        ?: return ToolResult.error("Missing 'event_id' for delete. Use list/search to find event IDs.")
                    deleteEvent(eventId)
                }
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: SecurityException) {
            ToolResult.error("Calendar permission not granted. Please grant calendar access in Settings.")
        } catch (e: Exception) {
            ToolResult.error("Calendar error: ${e.message}")
        }
    }

    private fun listEvents(days: Int): ToolResult {
        val resolver = context.contentResolver
        val now = System.currentTimeMillis()
        val end = now + days.toLong() * 24 * 60 * 60 * 1000

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.ALL_DAY,
        )

        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(now.toString(), end.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC"

        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        ) ?: return ToolResult.success("No calendar events found.")

        val sb = StringBuilder("Upcoming events (next $days days):\n\n")
        var count = 0
        val dateFormat = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())

        cursor.use {
            while (it.moveToNext() && count < 50) {
                val eventId = it.getLong(0)
                val title = it.getString(1) ?: "Untitled"
                val start = it.getLong(2)
                val endTime = it.getLong(3)
                val location = it.getString(4) ?: ""
                val desc = it.getString(5) ?: ""
                val allDay = it.getInt(6) == 1

                sb.append("- **$title** (ID: $eventId)\n")
                if (allDay) {
                    sb.append("  All day\n")
                } else {
                    sb.append("  ${dateFormat.format(start)}")
                    if (endTime > 0) sb.append(" - ${dateFormat.format(endTime)}")
                    sb.append("\n")
                }
                if (location.isNotBlank()) sb.append("  Location: $location\n")
                if (desc.isNotBlank()) sb.append("  Note: ${desc.take(100)}\n")
                sb.append("\n")
                count++
            }
        }

        if (count == 0) sb.append("No events scheduled.")
        return ToolResult.success(sb.toString())
    }

    private fun searchEvents(query: String): ToolResult {
        val resolver = context.contentResolver
        val now = System.currentTimeMillis()
        val sixMonths = now + 180L * 24 * 60 * 60 * 1000

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.EVENT_LOCATION,
        )

        val selection = "${CalendarContract.Events.TITLE} LIKE ? AND ${CalendarContract.Events.DTSTART} >= ?"
        val selectionArgs = arrayOf("%$query%", now.toString())

        val cursor = resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection, selection, selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
        ) ?: return ToolResult.success("No events matching '$query'.")

        val sb = StringBuilder("Events matching '$query':\n\n")
        var count = 0
        val dateFormat = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())

        cursor.use {
            while (it.moveToNext() && count < 20) {
                val eventId = it.getLong(0)
                val title = it.getString(1) ?: "Untitled"
                val start = it.getLong(2)
                sb.append("- **$title** (ID: $eventId) — ${dateFormat.format(start)}\n")
                count++
            }
        }

        if (count == 0) sb.append("No events found.")
        return ToolResult.success(sb.toString())
    }

    private fun createEvent(args: JsonObject): ToolResult {
        val title = args["title"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'title' for event creation")
        val startStr = args["start_time"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'start_time' for event creation")

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val startMillis = try {
            isoFormat.parse(startStr)?.time ?: return ToolResult.error("Invalid start_time format")
        } catch (e: Exception) {
            return ToolResult.error("Invalid start_time format. Use ISO 8601: yyyy-MM-ddTHH:mm:ss")
        }

        val endStr = args["end_time"]?.jsonPrimitive?.contentOrNull
        val endMillis = if (endStr != null) {
            try { isoFormat.parse(endStr)?.time ?: (startMillis + 3600000) }
            catch (e: Exception) { startMillis + 3600000 }
        } else {
            startMillis + 3600000 // Default 1 hour
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.CALENDAR_ID, getDefaultCalendarId())
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            args["description_text"]?.jsonPrimitive?.contentOrNull?.let {
                put(CalendarContract.Events.DESCRIPTION, it)
            }
            args["location"]?.jsonPrimitive?.contentOrNull?.let {
                put(CalendarContract.Events.EVENT_LOCATION, it)
            }
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return ToolResult.error("Failed to create event")

        val dateFormat = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())
        return ToolResult.success("Created event: '$title' on ${dateFormat.format(startMillis)}")
    }

    private fun deleteEvent(eventIdStr: String): ToolResult {
        val eventId = eventIdStr.toLongOrNull()
            ?: return ToolResult.error("Invalid event ID: '$eventIdStr'. Must be a number.")

        val uri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rowsDeleted = context.contentResolver.delete(uri, null, null)

        return if (rowsDeleted > 0) {
            ToolResult.success("Deleted event (ID: $eventId).")
        } else {
            ToolResult.error("No event found with ID $eventId, or it could not be deleted.")
        }
    }

    private fun getDefaultCalendarId(): Long {
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.IS_PRIMARY} = 1",
            null, null
        )
        cursor?.use {
            if (it.moveToFirst()) return it.getLong(0)
        }
        // Fallback: use first calendar
        val fallback = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            null, null, null
        )
        fallback?.use {
            if (it.moveToFirst()) return it.getLong(0)
        }
        return 1L
    }
}
