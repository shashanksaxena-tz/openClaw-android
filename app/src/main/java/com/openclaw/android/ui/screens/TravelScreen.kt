package com.openclaw.android.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

private val BgBlack = Color(0xFF050508)
private val SurfaceLight = Color(0xFF16161D)
private val Violet = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
private val Pink = Color(0xFFEC4899)
private val Green = Color(0xFF22C55E)
private val Amber = Color(0xFFF59E0B)
private val Red = Color(0xFFEF4444)
private val Blue = Color(0xFF3B82F6)
private val TextPrimary = Color(0xFFEEEEF0)
private val TextSecondary = Color(0xFF9CA3AF)
private val TextMuted = Color(0xFF6B7280)
private val GlassBg = Color(0xFF0D0D12).copy(alpha = 0.65f)
private val GlassBorder = Color.White.copy(alpha = 0.08f)
private val CardShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)

private val itineraryTypeIcons = mapOf(
    "flight" to Icons.Outlined.Flight,
    "hotel" to Icons.Outlined.Hotel,
    "car_rental" to Icons.Outlined.DirectionsCar,
    "activity" to Icons.Outlined.Attractions,
    "meeting" to Icons.Outlined.Groups,
    "transfer" to Icons.Outlined.LocalTaxi,
    "restaurant" to Icons.Outlined.Restaurant,
)

private val itineraryTypeColors = mapOf(
    "flight" to Cyan,
    "hotel" to Violet,
    "car_rental" to Amber,
    "activity" to Pink,
    "meeting" to Blue,
    "transfer" to Green,
    "restaurant" to Red,
)

@Composable
fun TravelScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("travel_manager", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    val trips = remember {
        try {
            val raw = prefs.getString("trips_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }
    val itinerary = remember {
        try {
            val raw = prefs.getString("itinerary_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }
    val documents = remember {
        try {
            val raw = prefs.getString("documents_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }

    var selectedFilter by remember { mutableStateOf("all") }
    var selectedTripId by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val now = System.currentTimeMillis()
    val filteredTrips = trips.filter { t ->
        val status = t.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: "upcoming"
        when (selectedFilter) {
            "upcoming" -> status == "upcoming" || (t.jsonObject["startDate"]?.jsonPrimitive?.longOrNull ?: 0) > now
            "active" -> status == "in_progress"
            "past" -> status == "completed" || (t.jsonObject["endDate"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE) < now
            else -> true
        }
    }.sortedBy { it.jsonObject["startDate"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE }

    val selectedTrip = selectedTripId?.let { id ->
        trips.find { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull == id }
    }

    Column(modifier = modifier.fillMaxSize().background(BgBlack)) {
        // ── Header ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 2 },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedTrip != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = TextSecondary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable { selectedTripId = null },
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                selectedTrip.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "Trip",
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    brush = Brush.linearGradient(listOf(Amber, Pink)),
                                ),
                            )
                        }
                    } else {
                        Text(
                            "Travel",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                brush = Brush.linearGradient(listOf(Amber, Pink)),
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(GlassBg)
                                .border(0.5.dp, GlassBorder, PillShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("${trips.size} trip${if (trips.size != 1) "s" else ""}", style = TextStyle(fontSize = 12.sp, color = TextMuted))
                        }
                    }
                }
            }
        }

        if (selectedTrip != null) {
            // ── Trip Detail View ─────────────────────────────────────────
            TripDetailView(
                trip = selectedTrip,
                itinerary = itinerary.filter { it.jsonObject["tripId"]?.jsonPrimitive?.contentOrNull == selectedTripId },
                documents = documents.filter { it.jsonObject["tripId"]?.jsonPrimitive?.contentOrNull == selectedTripId },
                onNavigateToChat = onNavigateToChat,
            )
        } else {
            // ── Filter Chips ─────────────────────────────────────────────
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 100)),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val filters = listOf("all" to "All", "upcoming" to "Upcoming", "active" to "Active", "past" to "Past")
                    items(filters) { (key, label) ->
                        TravelFilterChip(label = label, selected = selectedFilter == key, onClick = { selectedFilter = key })
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Upcoming Trip Banner ─────────────────────────────────────
            val nextTrip = trips
                .filter { (it.jsonObject["startDate"]?.jsonPrimitive?.longOrNull ?: 0) > now }
                .minByOrNull { it.jsonObject["startDate"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE }

            if (nextTrip != null) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 150)) + slideInVertically(tween(400, 150)) { it / 3 },
                ) {
                    NextTripBanner(trip = nextTrip, onClick = {
                        selectedTripId = nextTrip.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    })
                }
                Spacer(Modifier.height(12.dp))
            }

            // ── Trip List ────────────────────────────────────────────────
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (filteredTrips.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.FlightTakeoff, contentDescription = null, tint = Amber, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No trips yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                Text("Plan your next adventure with AI", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(Brush.linearGradient(listOf(Amber, Pink)))
                                        .clickable(onClick = onNavigateToChat)
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text("Plan a trip", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                                }
                            }
                        }
                    }
                }

                items(filteredTrips, key = { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString() }) { trip ->
                    TripCard(trip = trip, itineraryCount = itinerary.count {
                        it.jsonObject["tripId"]?.jsonPrimitive?.contentOrNull == trip.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    }, onClick = {
                        selectedTripId = trip.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                    })
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun NextTripBanner(trip: JsonElement, onClick: () -> Unit) {
    val name = trip.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "Trip"
    val destination = trip.jsonObject["destination"]?.jsonPrimitive?.contentOrNull ?: ""
    val startDate = trip.jsonObject["startDate"]?.jsonPrimitive?.longOrNull ?: 0
    val daysUntil = ((startDate - System.currentTimeMillis()) / 86400000).toInt().coerceAtLeast(0)
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(Amber.copy(alpha = 0.15f), Pink.copy(alpha = 0.10f))))
            .border(0.5.dp, Amber.copy(alpha = 0.25f), CardShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Amber.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.FlightTakeoff, contentDescription = null, tint = Amber, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                if (destination.isNotBlank()) {
                    Text(destination, style = TextStyle(fontSize = 13.sp, color = TextSecondary))
                }
                Text("${df.format(Date(startDate))} • in $daysUntil day${if (daysUntil != 1) "s" else ""}", style = TextStyle(fontSize = 12.sp, color = Amber))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Amber.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun TripCard(trip: JsonElement, itineraryCount: Int, onClick: () -> Unit) {
    val name = trip.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "Trip"
    val destination = trip.jsonObject["destination"]?.jsonPrimitive?.contentOrNull ?: ""
    val startDate = trip.jsonObject["startDate"]?.jsonPrimitive?.longOrNull ?: 0
    val endDate = trip.jsonObject["endDate"]?.jsonPrimitive?.longOrNull ?: 0
    val status = trip.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: "upcoming"
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val statusColor = when (status) { "in_progress" -> Green; "completed" -> TextMuted; else -> Cyan }
    val statusLabel = when (status) { "in_progress" -> "Active"; "completed" -> "Completed"; else -> "Upcoming" }
    val days = if (endDate > startDate) ((endDate - startDate) / 86400000).toInt() else 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(0.5.dp, GlassBorder, CardShape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.FlightTakeoff, contentDescription = null, tint = Amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(name, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                }
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(statusColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(statusLabel, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = statusColor))
                }
            }

            if (destination.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(destination, style = TextStyle(fontSize = 13.sp, color = TextSecondary))
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${df.format(Date(startDate))} – ${df.format(Date(endDate))}",
                        style = TextStyle(fontSize = 12.sp, color = TextMuted),
                    )
                }
                if (days > 0) {
                    Text("${days}d", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Amber))
                }
                if (itineraryCount > 0) {
                    Text("$itineraryCount items", style = TextStyle(fontSize = 12.sp, color = TextMuted))
                }
            }
        }
    }
}

@Composable
private fun TripDetailView(
    trip: JsonElement,
    itinerary: List<JsonElement>,
    documents: List<JsonElement>,
    onNavigateToChat: () -> Unit,
) {
    val destination = trip.jsonObject["destination"]?.jsonPrimitive?.contentOrNull ?: ""
    val startDate = trip.jsonObject["startDate"]?.jsonPrimitive?.longOrNull ?: 0
    val endDate = trip.jsonObject["endDate"]?.jsonPrimitive?.longOrNull ?: 0
    val notes = trip.jsonObject["notes"]?.jsonPrimitive?.contentOrNull ?: ""
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val daysUntil = ((startDate - System.currentTimeMillis()) / 86400000).toInt()

    val sortedItinerary = itinerary.sortedBy { it.jsonObject["dateTime"]?.jsonPrimitive?.longOrNull ?: 0 }

    // Group itinerary by day
    val dayFormatter = remember { SimpleDateFormat("EEEE, MMM d", Locale.getDefault()) }
    val groupedItinerary = sortedItinerary.groupBy { item ->
        val dt = item.jsonObject["dateTime"]?.jsonPrimitive?.longOrNull ?: 0
        val cal = Calendar.getInstance().apply { timeInMillis = dt }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        cal.timeInMillis
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Trip info banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CardShape)
                    .background(Brush.linearGradient(listOf(Amber.copy(alpha = 0.12f), Pink.copy(alpha = 0.08f))))
                    .border(0.5.dp, Amber.copy(alpha = 0.20f), CardShape)
                    .padding(16.dp),
            ) {
                Column {
                    if (destination.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(destination, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("Departure", style = TextStyle(fontSize = 11.sp, color = TextMuted))
                            Text(df.format(Date(startDate)), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary))
                        }
                        Column {
                            Text("Return", style = TextStyle(fontSize = 11.sp, color = TextMuted))
                            Text(df.format(Date(endDate)), style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary))
                        }
                        if (daysUntil > 0) {
                            Column {
                                Text("In", style = TextStyle(fontSize = 11.sp, color = TextMuted))
                                Text("$daysUntil day${if (daysUntil != 1) "s" else ""}", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Amber))
                            }
                        }
                    }
                    if (notes.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(notes, style = TextStyle(fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp))
                    }
                }
            }
        }

        // Itinerary timeline
        if (sortedItinerary.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Map, contentDescription = null, tint = Violet, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Itinerary", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                    Spacer(Modifier.width(8.dp))
                    Text("${sortedItinerary.size} items", style = TextStyle(fontSize = 12.sp, color = TextMuted))
                }
            }

            groupedItinerary.forEach { (dayMillis, items) ->
                item {
                    Text(
                        dayFormatter.format(Date(dayMillis)),
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Cyan),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(items) { item ->
                    ItineraryItemCard(item = item)
                }
            }
        } else {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(SurfaceLight)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No itinerary items yet", style = TextStyle(fontSize = 14.sp, color = TextMuted))
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(Brush.linearGradient(listOf(Amber, Pink)))
                                .clickable(onClick = onNavigateToChat)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text("Add via AI", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                        }
                    }
                }
            }
        }

        // Documents
        if (documents.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Documents", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                }
            }
            items(documents) { doc ->
                DocumentCard(doc = doc)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ItineraryItemCard(item: JsonElement) {
    val type = item.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "activity"
    val title = item.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: ""
    val dateTime = item.jsonObject["dateTime"]?.jsonPrimitive?.longOrNull ?: 0
    val location = item.jsonObject["location"]?.jsonPrimitive?.contentOrNull ?: ""
    val confirmation = item.jsonObject["confirmationNumber"]?.jsonPrimitive?.contentOrNull ?: ""
    val itemNotes = item.jsonObject["notes"]?.jsonPrimitive?.contentOrNull ?: ""
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val color = itineraryTypeColors[type] ?: TextMuted
    val icon = itineraryTypeIcons[type] ?: Icons.Outlined.Event

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(0.5.dp, GlassBorder, CardShape)
            .padding(14.dp),
    ) {
        // Type icon
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary), modifier = Modifier.weight(1f))
                Text(timeFormatter.format(Date(dateTime)), style = TextStyle(fontSize = 11.sp, color = color))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(color.copy(alpha = 0.10f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(type.replace("_", " "), style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color))
                }
                if (location.isNotBlank()) {
                    Text(location, style = TextStyle(fontSize = 11.sp, color = TextMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (confirmation.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("Conf: $confirmation", style = TextStyle(fontSize = 11.sp, color = TextSecondary))
            }
            if (itemNotes.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(itemNotes, style = TextStyle(fontSize = 11.sp, color = TextMuted), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun DocumentCard(doc: JsonElement) {
    val type = doc.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: ""
    val description = doc.jsonObject["description"]?.jsonPrimitive?.contentOrNull ?: ""
    val expiryDate = doc.jsonObject["expiryDate"]?.jsonPrimitive?.longOrNull
    val df = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(0.5.dp, GlassBorder, CardShape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Green.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Description, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(description.ifBlank { type }, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (type.isNotBlank()) {
                    Text(type, style = TextStyle(fontSize = 11.sp, color = TextMuted))
                }
                if (expiryDate != null) {
                    val isExpired = expiryDate < System.currentTimeMillis()
                    Text(
                        "Exp: ${df.format(Date(expiryDate))}",
                        style = TextStyle(fontSize = 11.sp, color = if (isExpired) Red else TextMuted),
                    )
                }
            }
        }
    }
}

@Composable
private fun TravelFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) Amber.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) Amber.copy(alpha = 0.5f) else GlassBorder,
                shape = PillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Amber else TextSecondary,
            ),
        )
    }
}
