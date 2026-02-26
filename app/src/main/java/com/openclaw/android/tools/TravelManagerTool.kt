package com.openclaw.android.tools

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Travel Management tool.
 * Manages trips, itineraries, documents, and travel reminders.
 */
class TravelManagerTool(context: Context) : Tool {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("travel_manager", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Trip(
        val id: String,
        val name: String,
        val destination: String,
        val startDate: Long,
        val endDate: Long,
        val status: String = "upcoming",
        val notes: String = "",
        val createdAt: Long,
    )

    @Serializable
    data class ItineraryItem(
        val id: String,
        val tripId: String,
        val type: String,
        val title: String,
        val dateTime: Long,
        val location: String = "",
        val confirmationNumber: String = "",
        val notes: String = "",
    )

    @Serializable
    data class TravelDocument(
        val id: String,
        val tripId: String,
        val type: String,
        val description: String,
        val filePath: String = "",
        val expiryDate: Long? = null,
    )

    override val name = "travel_manager"
    override val description = "Manage travel plans, trips, itineraries, and documents. " +
            "Actions: 'create_trip' (new trip), 'list_trips' (list all trips), " +
            "'add_itinerary' (add flight/hotel/activity to trip), 'view_trip' (full trip details), " +
            "'add_document' (attach document to trip), 'delete_trip' (remove trip), " +
            "'upcoming' (upcoming travel), 'checklist' (packing/prep checklist for trip)."

    override val parameterSchema = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("create_trip"); add("list_trips"); add("add_itinerary")
                    add("view_trip"); add("add_document"); add("delete_trip")
                    add("upcoming"); add("checklist")
                })
            }
            putJsonObject("trip_id") { put("type", "string"); put("description", "Trip ID") }
            putJsonObject("name") { put("type", "string"); put("description", "Trip name") }
            putJsonObject("destination") { put("type", "string"); put("description", "Destination city/country") }
            putJsonObject("start_date") { put("type", "string"); put("description", "Start date (ISO 8601: 2024-03-15)") }
            putJsonObject("end_date") { put("type", "string"); put("description", "End date (ISO 8601: 2024-03-20)") }
            putJsonObject("type") {
                put("type", "string")
                put("enum", buildJsonArray {
                    add("flight"); add("hotel"); add("car_rental"); add("activity")
                    add("meeting"); add("transfer"); add("restaurant")
                })
                put("description", "Itinerary item type or document type")
            }
            putJsonObject("title") { put("type", "string"); put("description", "Item title") }
            putJsonObject("date_time") { put("type", "string"); put("description", "Date/time (ISO 8601)") }
            putJsonObject("location") { put("type", "string"); put("description", "Location/address") }
            putJsonObject("confirmation_number") { put("type", "string"); put("description", "Booking confirmation number") }
            putJsonObject("notes") { put("type", "string"); put("description", "Additional notes") }
            putJsonObject("description") { put("type", "string"); put("description", "Document description") }
            putJsonObject("expiry_date") { put("type", "string"); put("description", "Document expiry date") }
        }
        putJsonArray("required") { add("action") }
    }

    override suspend fun execute(arguments: JsonElement): ToolResult {
        val args = arguments.jsonObject
        val action = args["action"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'action' parameter")

        return try {
            when (action) {
                "create_trip" -> createTrip(args)
                "list_trips" -> listTrips()
                "add_itinerary" -> addItinerary(args)
                "view_trip" -> viewTrip(args)
                "add_document" -> addDocument(args)
                "delete_trip" -> deleteTrip(args)
                "upcoming" -> upcomingTravel()
                "checklist" -> tripChecklist(args)
                else -> ToolResult.error("Unknown action: $action")
            }
        } catch (e: Exception) {
            ToolResult.error("Travel manager error: ${e.message}")
        }
    }

    private fun createTrip(args: JsonObject): ToolResult {
        val name = args["name"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'name'")
        val destination = args["destination"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'destination'")
        val startDate = args["start_date"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) }
            ?: return ToolResult.error("Missing or invalid 'start_date'")
        val endDate = args["end_date"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) }
            ?: return ToolResult.error("Missing or invalid 'end_date'")

        val id = UUID.randomUUID().toString().take(8)
        val trip = Trip(
            id = id, name = name, destination = destination,
            startDate = startDate, endDate = endDate,
            notes = args["notes"]?.jsonPrimitive?.contentOrNull ?: "",
            createdAt = System.currentTimeMillis(),
        )

        val trips = getAllTrips().toMutableList()
        trips.add(trip)
        saveTrips(trips)

        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        return ToolResult.success(
            "Trip created (ID: $id)\n" +
            "Name: $name\n" +
            "Destination: $destination\n" +
            "Dates: ${df.format(Date(startDate))} - ${df.format(Date(endDate))}\n"
        )
    }

    private fun listTrips(): ToolResult {
        val trips = getAllTrips().sortedBy { it.startDate }
        if (trips.isEmpty()) return ToolResult.success("No trips planned.")

        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val sb = StringBuilder("All Trips (${trips.size}):\n\n")
        for (t in trips) {
            val status = when {
                t.endDate < System.currentTimeMillis() -> "completed"
                t.startDate <= System.currentTimeMillis() -> "in_progress"
                else -> "upcoming"
            }
            sb.append("- **${t.name}** to ${t.destination} (ID: ${t.id})\n")
            sb.append("  ${df.format(Date(t.startDate))} - ${df.format(Date(t.endDate))} [$status]\n\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun addItinerary(args: JsonObject): ToolResult {
        val tripId = args["trip_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'trip_id'")
        val type = args["type"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'type'")
        val title = args["title"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'title'")
        val dateTime = args["date_time"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) }
            ?: return ToolResult.error("Missing or invalid 'date_time'")

        val trips = getAllTrips()
        if (trips.none { it.id == tripId }) return ToolResult.error("Trip '$tripId' not found.")

        val id = UUID.randomUUID().toString().take(8)
        val item = ItineraryItem(
            id = id, tripId = tripId, type = type, title = title,
            dateTime = dateTime,
            location = args["location"]?.jsonPrimitive?.contentOrNull ?: "",
            confirmationNumber = args["confirmation_number"]?.jsonPrimitive?.contentOrNull ?: "",
            notes = args["notes"]?.jsonPrimitive?.contentOrNull ?: "",
        )

        val items = getAllItinerary().toMutableList()
        items.add(item)
        saveItinerary(items)

        return ToolResult.success("Added $type '$title' to trip (ID: $id)")
    }

    private fun viewTrip(args: JsonObject): ToolResult {
        val tripId = args["trip_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'trip_id'")

        val trip = getAllTrips().find { it.id == tripId }
            ?: return ToolResult.error("Trip '$tripId' not found.")
        val items = getAllItinerary().filter { it.tripId == tripId }.sortedBy { it.dateTime }
        val docs = getAllDocuments().filter { it.tripId == tripId }

        val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val tf = SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault())

        val sb = StringBuilder("## ${trip.name}\n")
        sb.append("Destination: ${trip.destination}\n")
        sb.append("Dates: ${df.format(Date(trip.startDate))} - ${df.format(Date(trip.endDate))}\n")
        if (trip.notes.isNotBlank()) sb.append("Notes: ${trip.notes}\n")
        sb.append("\n")

        if (items.isNotEmpty()) {
            sb.append("### Itinerary (${items.size} items)\n")
            for (item in items) {
                val icon = when (item.type) {
                    "flight" -> "="; "hotel" -> "#"; "car_rental" -> ">"
                    "meeting" -> "@"; "restaurant" -> "*"; else -> "-"
                }
                sb.append("$icon **${item.title}** [${item.type}]\n")
                sb.append("  ${tf.format(Date(item.dateTime))}")
                if (item.location.isNotBlank()) sb.append(" | ${item.location}")
                if (item.confirmationNumber.isNotBlank()) sb.append(" | Conf: ${item.confirmationNumber}")
                sb.append("\n")
            }
            sb.append("\n")
        }

        if (docs.isNotEmpty()) {
            sb.append("### Documents (${docs.size})\n")
            for (doc in docs) {
                sb.append("- [${doc.type}] ${doc.description}\n")
            }
        }

        return ToolResult.success(sb.toString())
    }

    private fun addDocument(args: JsonObject): ToolResult {
        val tripId = args["trip_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'trip_id'")
        val type = args["type"]?.jsonPrimitive?.contentOrNull ?: "document"
        val description = args["description"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'description'")

        val id = UUID.randomUUID().toString().take(8)
        val doc = TravelDocument(
            id = id, tripId = tripId, type = type,
            description = description,
            expiryDate = args["expiry_date"]?.jsonPrimitive?.contentOrNull?.let { parseDate(it) },
        )

        val docs = getAllDocuments().toMutableList()
        docs.add(doc)
        saveDocuments(docs)

        return ToolResult.success("Document added to trip: $description (ID: $id)")
    }

    private fun deleteTrip(args: JsonObject): ToolResult {
        val tripId = args["trip_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'trip_id'")

        val trips = getAllTrips().toMutableList()
        val removed = trips.removeAll { it.id == tripId }
        if (!removed) return ToolResult.error("Trip '$tripId' not found.")

        // Also clean up itinerary and documents
        val items = getAllItinerary().toMutableList()
        items.removeAll { it.tripId == tripId }
        val docs = getAllDocuments().toMutableList()
        docs.removeAll { it.tripId == tripId }

        saveTrips(trips)
        saveItinerary(items)
        saveDocuments(docs)

        return ToolResult.success("Trip '$tripId' and all related items deleted.")
    }

    private fun upcomingTravel(): ToolResult {
        val now = System.currentTimeMillis()
        val trips = getAllTrips().filter { it.endDate >= now }.sortedBy { it.startDate }
        if (trips.isEmpty()) return ToolResult.success("No upcoming travel.")

        val df = SimpleDateFormat("MMM d", Locale.getDefault())
        val sb = StringBuilder("Upcoming Travel:\n\n")
        for (t in trips) {
            val daysUntil = ((t.startDate - now) / (24 * 60 * 60 * 1000)).toInt()
            val status = if (daysUntil <= 0) "IN PROGRESS" else "in $daysUntil days"
            sb.append("- **${t.name}** to ${t.destination} ($status)\n")
            sb.append("  ${df.format(Date(t.startDate))} - ${df.format(Date(t.endDate))}\n")

            val items = getAllItinerary().filter { it.tripId == t.id }.size
            if (items > 0) sb.append("  $items itinerary items\n")
            sb.append("\n")
        }
        return ToolResult.success(sb.toString())
    }

    private fun tripChecklist(args: JsonObject): ToolResult {
        val tripId = args["trip_id"]?.jsonPrimitive?.contentOrNull
            ?: return ToolResult.error("Missing 'trip_id'")

        val trip = getAllTrips().find { it.id == tripId }
            ?: return ToolResult.error("Trip '$tripId' not found.")
        val items = getAllItinerary().filter { it.tripId == tripId }
        val docs = getAllDocuments().filter { it.tripId == tripId }

        val df = SimpleDateFormat("MMM d", Locale.getDefault())
        val daysUntil = ((trip.startDate - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)).toInt()

        val sb = StringBuilder("## Pre-Trip Checklist: ${trip.name}\n")
        sb.append("Destination: ${trip.destination} | Departure: ${df.format(Date(trip.startDate))}")
        if (daysUntil > 0) sb.append(" ($daysUntil days away)")
        sb.append("\n\n")

        sb.append("### Travel Documents\n")
        val hasFlights = items.any { it.type == "flight" }
        val hasHotels = items.any { it.type == "hotel" }
        sb.append("- [ ] Passport / ID valid\n")
        if (hasFlights) sb.append("- [${if (items.any { it.type == "flight" && it.confirmationNumber.isNotBlank() }) "x" else " "}] Flight booking confirmed\n")
        if (hasHotels) sb.append("- [${if (items.any { it.type == "hotel" && it.confirmationNumber.isNotBlank() }) "x" else " "}] Hotel booking confirmed\n")
        sb.append("- [${if (docs.isNotEmpty()) "x" else " "}] Travel documents uploaded\n\n")

        sb.append("### Packing\n")
        sb.append("- [ ] Clothing for ${((trip.endDate - trip.startDate) / (24 * 60 * 60 * 1000)).toInt()} days\n")
        sb.append("- [ ] Chargers and electronics\n")
        sb.append("- [ ] Toiletries\n")
        sb.append("- [ ] Medications\n\n")

        sb.append("### Before Departure\n")
        sb.append("- [ ] Check-in online (24h before)\n")
        sb.append("- [ ] Download boarding passes\n")
        sb.append("- [ ] Set out-of-office\n")
        sb.append("- [ ] Arrange transportation to airport\n")

        return ToolResult.success(sb.toString())
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

    private fun getAllTrips(): List<Trip> {
        val raw = prefs.getString("trips_data", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }
    private fun saveTrips(trips: List<Trip>) {
        prefs.edit().putString("trips_data", json.encodeToString(trips)).apply()
    }
    private fun getAllItinerary(): List<ItineraryItem> {
        val raw = prefs.getString("itinerary_data", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }
    private fun saveItinerary(items: List<ItineraryItem>) {
        prefs.edit().putString("itinerary_data", json.encodeToString(items)).apply()
    }
    private fun getAllDocuments(): List<TravelDocument> {
        val raw = prefs.getString("documents_data", "[]") ?: "[]"
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }
    private fun saveDocuments(docs: List<TravelDocument>) {
        prefs.edit().putString("documents_data", json.encodeToString(docs)).apply()
    }
}
