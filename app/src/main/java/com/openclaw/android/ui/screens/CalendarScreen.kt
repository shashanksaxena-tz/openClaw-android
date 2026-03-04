package com.openclaw.android.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
private val TextPrimary = Color(0xFFEEEEF0)
private val TextSecondary = Color(0xFF9CA3AF)
private val TextMuted = Color(0xFF6B7280)
private val GlassBg = Color(0xFF0D0D12).copy(alpha = 0.65f)
private val GlassBorder = Color.White.copy(alpha = 0.08f)
private val CardShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)

private data class CalEvent(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val location: String,
    val description: String,
    val calendarColor: Int?,
    val allDay: Boolean,
)

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
) {
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(Calendar.getInstance()) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Load events for the selected week/month
    val events = remember(selectedDate.timeInMillis) {
        getEventsForDay(context, selectedDate)
    }

    // Load tasks with deadlines for the selected day
    val json = remember { Json { ignoreUnknownKeys = true } }
    val taskPrefs = remember { context.getSharedPreferences("task_manager", Context.MODE_PRIVATE) }
    val tasksOnDate = remember(selectedDate.timeInMillis) {
        try {
            val raw = taskPrefs.getString("tasks_data", "[]") ?: "[]"
            val arr = json.parseToJsonElement(raw).jsonArray
            val dayStart = selectedDate.clone() as Calendar
            dayStart.set(Calendar.HOUR_OF_DAY, 0); dayStart.set(Calendar.MINUTE, 0); dayStart.set(Calendar.SECOND, 0)
            val dayEnd = dayStart.clone() as Calendar
            dayEnd.add(Calendar.DAY_OF_YEAR, 1)
            arr.filter { task ->
                val deadline = task.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: return@filter false
                deadline in dayStart.timeInMillis until dayEnd.timeInMillis
            }.map { it.jsonObject }
        } catch (_: Exception) { emptyList() }
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
                    Text(
                        "Calendar",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(listOf(Cyan, Violet)),
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(GlassBg)
                                .border(0.5.dp, GlassBorder, PillShape)
                                .clickable {
                                    selectedDate = Calendar.getInstance()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text("Today", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cyan))
                        }
                    }
                }
            }
        }

        // ── Month Header with Navigation ─────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400, 100)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous", tint = TextSecondary)
                }
                Text(
                    SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(selectedDate.time),
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                )
                IconButton(onClick = {
                    selectedDate = (selectedDate.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = TextSecondary)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Day of Week Headers ──────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400, 150)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
                for (label in dayLabels) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextMuted),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Calendar Grid ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400, 200)) + slideInVertically(tween(400, 200)) { it / 4 },
        ) {
            CalendarGrid(
                selectedDate = selectedDate,
                context = context,
                onDateSelected = { cal ->
                    selectedDate = cal
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Selected Day Header ──────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400, 300)),
        ) {
            val dayFormatter = remember { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    dayFormatter.format(selectedDate.time),
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                )
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Violet.copy(alpha = 0.10f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "${events.size} event${if (events.size != 1) "s" else ""}",
                        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Violet),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Events + Tasks for Selected Day ──────────────────────────────
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (events.isEmpty() && tasksOnDate.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = Cyan, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No events", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                            Text("This day is free for deep work", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(Brush.linearGradient(listOf(Cyan, Violet)))
                                    .clickable(onClick = onNavigateToChat)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text("Plan this day with AI", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                            }
                        }
                    }
                }
            }

            // Time blocks
            if (events.isNotEmpty()) {
                items(events.sortedBy { it.start }) { event ->
                    CalendarEventCard(event = event)
                }
            }

            // Tasks due on this day
            if (tasksOnDate.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Violet, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tasks Due", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                    }
                }
                items(tasksOnDate) { task ->
                    TaskDueCard(task = task)
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CalendarGrid(
    selectedDate: Calendar,
    context: Context,
    onDateSelected: (Calendar) -> Unit,
) {
    val today = remember { Calendar.getInstance() }
    val monthStart = (selectedDate.clone() as Calendar).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val firstDayOfWeek = monthStart.get(Calendar.DAY_OF_WEEK) - 1 // 0 = Sunday
    val daysInMonth = selectedDate.getActualMaximum(Calendar.DAY_OF_MONTH)
    val selectedDay = selectedDate.get(Calendar.DAY_OF_MONTH)
    val selectedMonth = selectedDate.get(Calendar.MONTH)
    val selectedYear = selectedDate.get(Calendar.YEAR)
    val todayDay = today.get(Calendar.DAY_OF_MONTH)
    val todayMonth = today.get(Calendar.MONTH)
    val todayYear = today.get(Calendar.YEAR)

    // Get days with events in this month
    val daysWithEvents = remember(selectedMonth, selectedYear) {
        getDaysWithEventsInMonth(context, selectedYear, selectedMonth)
    }

    val totalCells = firstDayOfWeek + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val day = cellIndex - firstDayOfWeek + 1

                    if (day in 1..daysInMonth) {
                        val isSelected = day == selectedDay
                        val isToday = day == todayDay && selectedMonth == todayMonth && selectedYear == todayYear
                        val hasEvents = day in daysWithEvents

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> Violet.copy(alpha = 0.25f)
                                        isToday -> Cyan.copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isSelected) Modifier.border(1.dp, Violet.copy(alpha = 0.5f), CircleShape)
                                    else if (isToday) Modifier.border(0.5.dp, Cyan.copy(alpha = 0.3f), CircleShape)
                                    else Modifier
                                )
                                .clickable {
                                    val newCal = (selectedDate.clone() as Calendar).apply {
                                        set(Calendar.DAY_OF_MONTH, day)
                                    }
                                    onDateSelected(newCal)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$day",
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Violet
                                            isToday -> Cyan
                                            else -> TextPrimary
                                        },
                                    ),
                                )
                                if (hasEvents) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(
                                                if (isSelected) Violet else if (isToday) Cyan else TextMuted,
                                                CircleShape,
                                            ),
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEventCard(event: CalEvent) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val accentColor = if (event.calendarColor != null) Color(event.calendarColor) else Cyan

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(0.5.dp, GlassBorder, CardShape),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 60.dp)
                    .background(accentColor),
            )

            Column(modifier = Modifier.padding(14.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        event.title,
                        style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(accentColor.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (event.allDay) "All day"
                            else timeFormatter.format(Date(event.start)),
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = accentColor),
                        )
                    }
                }

                if (!event.allDay) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${timeFormatter.format(Date(event.start))} – ${timeFormatter.format(Date(event.end))}",
                        style = TextStyle(fontSize = 12.sp, color = TextMuted),
                    )
                    // Duration
                    val durationMin = ((event.end - event.start) / 60000).toInt()
                    val hours = durationMin / 60
                    val mins = durationMin % 60
                    val durationStr = if (hours > 0) "${hours}h${if (mins > 0) " ${mins}m" else ""}" else "${mins}m"
                    Text(
                        durationStr,
                        style = TextStyle(fontSize = 11.sp, color = TextMuted),
                    )
                }

                if (event.location.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = TextMuted, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            event.location,
                            style = TextStyle(fontSize = 12.sp, color = TextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (event.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        event.description,
                        style = TextStyle(fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskDueCard(task: JsonObject) {
    val title = task["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
    val priority = task["priority"]?.jsonPrimitive?.contentOrNull ?: "medium"
    val status = task["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
    val priorityColor = when (priority) { "high" -> Red; "medium" -> Amber; else -> TextMuted }

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
                .size(8.dp)
                .background(priorityColor, CircleShape),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary))
            Text("Task • $priority priority", style = TextStyle(fontSize = 11.sp, color = TextMuted))
        }
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(
                    if (status == "done") Green.copy(alpha = 0.12f) else Violet.copy(alpha = 0.12f)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        ) {
            Text(
                status.replace("_", " "),
                style = TextStyle(
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (status == "done") Green else Violet,
                ),
            )
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

private fun getEventsForDay(context: Context, date: Calendar): List<CalEvent> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    return try {
        val dayStart = (date.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val dayEnd = (dayStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.CALENDAR_COLOR,
            CalendarContract.Events.ALL_DAY,
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val args = arrayOf(dayStart.timeInMillis.toString(), dayEnd.timeInMillis.toString())

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, args,
            "${CalendarContract.Events.DTSTART} ASC",
        ) ?: return emptyList()

        val list = mutableListOf<CalEvent>()
        cursor.use {
            while (it.moveToNext()) {
                list.add(CalEvent(
                    id = it.getLong(0),
                    title = it.getString(1) ?: "Untitled",
                    start = it.getLong(2),
                    end = it.getLong(3).let { e -> if (e > 0) e else it.getLong(2) + 3600000 },
                    location = it.getString(4) ?: "",
                    description = it.getString(5) ?: "",
                    calendarColor = try { it.getInt(6) } catch (_: Exception) { null },
                    allDay = it.getInt(7) == 1,
                ))
            }
        }
        list
    } catch (_: Exception) { emptyList() }
}

private fun getDaysWithEventsInMonth(context: Context, year: Int, month: Int): Set<Int> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        return emptySet()
    }
    return try {
        val monthStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val monthEnd = (monthStart.clone() as Calendar).apply { add(Calendar.MONTH, 1) }

        val projection = arrayOf(CalendarContract.Events.DTSTART)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val args = arrayOf(monthStart.timeInMillis.toString(), monthEnd.timeInMillis.toString())

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, args, null,
        ) ?: return emptySet()

        val days = mutableSetOf<Int>()
        cursor.use {
            while (it.moveToNext()) {
                val cal = Calendar.getInstance().apply { timeInMillis = it.getLong(0) }
                days.add(cal.get(Calendar.DAY_OF_MONTH))
            }
        }
        days
    } catch (_: Exception) { emptySet() }
}
