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
private val Blue = Color(0xFF3B82F6)
private val TextPrimary = Color(0xFFEEEEF0)
private val TextSecondary = Color(0xFF9CA3AF)
private val TextMuted = Color(0xFF6B7280)
private val GlassBg = Color(0xFF0D0D12).copy(alpha = 0.65f)
private val GlassBorder = Color.White.copy(alpha = 0.08f)
private val CardShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)

@Composable
fun BriefingScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val json = remember { Json { ignoreUnknownKeys = true } }

    val taskPrefs = remember { context.getSharedPreferences("task_manager", Context.MODE_PRIVATE) }
    val notePrefs = remember { context.getSharedPreferences("smart_notes", Context.MODE_PRIVATE) }
    val teamPrefs = remember { context.getSharedPreferences("team_manager", Context.MODE_PRIVATE) }

    // Parse data
    val tasks = remember {
        try {
            val raw = taskPrefs.getString("tasks_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }
    val notes = remember {
        try {
            val raw = notePrefs.getString("notes_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }
    val members = remember {
        try {
            val raw = teamPrefs.getString("members_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }
    val delegations = remember {
        try {
            val raw = teamPrefs.getString("delegations_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }
    val teamNotes = remember {
        try {
            val raw = teamPrefs.getString("notes_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }

    // Calendar events
    val todayEvents = remember { getBriefingTodayEvents(context) }

    // Time calculations
    val now = System.currentTimeMillis()
    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Priority tasks (not done, sorted by priority + deadline)
    val priorityTasks = tasks
        .filter { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull != "done" }
        .sortedWith(compareBy<JsonElement> {
            when (it.jsonObject["priority"]?.jsonPrimitive?.contentOrNull) { "high" -> 0; "medium" -> 1; else -> 2 }
        }.thenBy { it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE })
        .take(10)

    // Overdue tasks
    val overdueTasks = tasks.filter {
        it.jsonObject["status"]?.jsonPrimitive?.contentOrNull != "done" &&
        (it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE) < now
    }

    // Overdue delegations
    val overdueDelegations = delegations.filter {
        it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "assigned" &&
        (it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE) < now
    }

    // Team check-ins due (no note in last 7 days)
    val weekAgo = now - 7 * 86400000L
    val checkInsDue = members.filter { member ->
        val memberId = member.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@filter false
        val lastNote = teamNotes
            .filter { it.jsonObject["memberId"]?.jsonPrimitive?.contentOrNull == memberId }
            .maxOfOrNull { it.jsonObject["date"]?.jsonPrimitive?.longOrNull ?: 0 } ?: 0
        lastNote < weekAgo
    }

    // Completed yesterday
    val yesterdayStart = todayStart - 86400000L
    val completedYesterday = tasks.count {
        it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" &&
        (it.jsonObject["completedAt"]?.jsonPrimitive?.longOrNull ?: 0) in yesterdayStart until todayStart
    }

    // Notes captured today
    val notesToday = notes.count {
        (it.jsonObject["createdAt"]?.jsonPrimitive?.longOrNull ?: 0) > todayStart
    }

    // Time blocks
    val timeBlocks = buildTimeBlocks(todayEvents, priorityTasks)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(BgBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header ───────────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 2 },
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(onClick = onBack),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Daily Briefing",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                brush = Brush.linearGradient(listOf(Amber, Pink)),
                            ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$greeting — ${SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())}",
                        style = TextStyle(fontSize = 14.sp, color = TextSecondary),
                        modifier = Modifier.padding(start = 36.dp),
                    )
                }
            }
        }

        // ── Quick Summary Cards ──────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 100)) + slideInVertically(tween(400, 100)) { it / 3 },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BriefingStat(value = "${todayEvents.size}", label = "Events", color = Cyan, modifier = Modifier.weight(1f))
                    BriefingStat(value = "${priorityTasks.size}", label = "Tasks", color = Violet, modifier = Modifier.weight(1f))
                    BriefingStat(value = "$completedYesterday", label = "Done yday", color = Green, modifier = Modifier.weight(1f))
                    BriefingStat(value = "$notesToday", label = "Notes", color = Pink, modifier = Modifier.weight(1f))
                }
            }
        }

        // ── Alerts Section ───────────────────────────────────────────────
        if (overdueTasks.isNotEmpty() || overdueDelegations.isNotEmpty() || checkInsDue.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 200)) + slideInVertically(tween(400, 200)) { it / 3 },
                ) {
                    BriefingSection(title = "Needs Attention", icon = Icons.Outlined.Warning, iconColor = Red) {
                        if (overdueTasks.isNotEmpty()) {
                            BriefingAlert(
                                text = "${overdueTasks.size} overdue task${if (overdueTasks.size > 1) "s" else ""}",
                                color = Red,
                                onClick = onNavigateToTasks,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        if (overdueDelegations.isNotEmpty()) {
                            BriefingAlert(
                                text = "${overdueDelegations.size} overdue delegation${if (overdueDelegations.size > 1) "s" else ""}",
                                color = Amber,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        if (checkInsDue.isNotEmpty()) {
                            val names = checkInsDue.take(3).mapNotNull {
                                it.jsonObject["name"]?.jsonPrimitive?.contentOrNull
                            }.joinToString(", ")
                            BriefingAlert(
                                text = "Check-in due: $names${if (checkInsDue.size > 3) " +${checkInsDue.size - 3} more" else ""}",
                                color = Cyan,
                            )
                        }
                    }
                }
            }
        }

        // ── Today's Schedule ─────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 300)) + slideInVertically(tween(400, 300)) { it / 3 },
            ) {
                BriefingSection(title = "Today's Schedule", icon = Icons.Outlined.CalendarMonth, iconColor = Cyan) {
                    if (todayEvents.isEmpty()) {
                        Text("No events today — perfect for focus work!", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                    } else {
                        val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                        for ((i, event) in todayEvents.take(8).withIndex()) {
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceLight)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(28.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Cyan),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(event.second, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(event.first, style = TextStyle(fontSize = 11.sp, color = TextMuted))
                                }
                            }
                        }
                        if (todayEvents.size > 8) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "+${todayEvents.size - 8} more events",
                                style = TextStyle(fontSize = 12.sp, color = Cyan),
                                modifier = Modifier.clickable(onClick = onNavigateToCalendar),
                            )
                        }
                    }
                }
            }
        }

        // ── Top Priorities ───────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 400)) + slideInVertically(tween(400, 400)) { it / 3 },
            ) {
                BriefingSection(title = "Top Priorities", icon = Icons.Outlined.PriorityHigh, iconColor = Violet) {
                    if (priorityTasks.isEmpty()) {
                        Text("No pending tasks. You're all caught up!", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                    } else {
                        val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
                        for ((i, task) in priorityTasks.take(6).withIndex()) {
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            val title = task.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                            val priority = task.jsonObject["priority"]?.jsonPrimitive?.contentOrNull ?: "medium"
                            val deadline = task.jsonObject["deadline"]?.jsonPrimitive?.longOrNull
                            val project = task.jsonObject["project"]?.jsonPrimitive?.contentOrNull ?: ""
                            val pColor = when (priority) { "high" -> Red; "medium" -> Amber; else -> TextMuted }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceLight)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("${i + 1}", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = pColor))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .clip(PillShape)
                                                .background(pColor.copy(alpha = 0.10f))
                                                .padding(horizontal = 6.dp, vertical = 1.dp),
                                        ) {
                                            Text(priority, style = TextStyle(fontSize = 9.sp, color = pColor))
                                        }
                                        if (project.isNotBlank() && project != "general") {
                                            Text(project, style = TextStyle(fontSize = 10.sp, color = TextMuted))
                                        }
                                    }
                                }
                                if (deadline != null) {
                                    val isOverdue = deadline < now
                                    Text(
                                        df.format(Date(deadline)),
                                        style = TextStyle(fontSize = 11.sp, color = if (isOverdue) Red else TextMuted),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Suggested Time Blocks ────────────────────────────────────────
        if (timeBlocks.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 500)) + slideInVertically(tween(400, 500)) { it / 3 },
                ) {
                    BriefingSection(title = "Suggested Time Blocks", icon = Icons.Outlined.Schedule, iconColor = Green) {
                        for ((i, block) in timeBlocks.withIndex()) {
                            if (i > 0) Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(block.color.copy(alpha = 0.06f))
                                    .border(0.5.dp, block.color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(block.icon, contentDescription = null, tint = block.color, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(block.label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary))
                                    Text(block.time, style = TextStyle(fontSize = 11.sp, color = TextMuted))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Ask AI for Deeper Briefing ───────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 600)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(Brush.linearGradient(listOf(Violet.copy(alpha = 0.12f), Cyan.copy(alpha = 0.08f))))
                        .border(0.5.dp, Violet.copy(alpha = 0.20f), CardShape)
                        .clickable(onClick = onNavigateToChat)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.SmartToy, contentDescription = null, tint = Violet, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Get AI-powered briefing", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                            Text("Deeper analysis, suggestions & action plan", style = TextStyle(fontSize = 12.sp, color = TextMuted))
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ─── Sub-Components ──────────────────────────────────────────────────────────

@Composable
private fun BriefingStat(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.15f), CardShape)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color))
            Text(label, style = TextStyle(fontSize = 10.sp, color = TextMuted))
        }
    }
}

@Composable
private fun BriefingSection(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(GlassBg)
            .border(0.5.dp, GlassBorder, CardShape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun BriefingAlert(text: String, color: Color, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.08f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = TextStyle(fontSize = 13.sp, color = color), modifier = Modifier.weight(1f))
        if (onClick != null) {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = color.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
        }
    }
}

// ─── Time Blocks ─────────────────────────────────────────────────────────────

private data class TimeBlock(
    val label: String,
    val time: String,
    val icon: ImageVector,
    val color: Color,
)

private fun buildTimeBlocks(
    events: List<Pair<String, String>>,
    priorityTasks: List<JsonElement>,
): List<TimeBlock> {
    val blocks = mutableListOf<TimeBlock>()
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    if (hour < 12) {
        blocks.add(TimeBlock("Morning focus block", "9:00 AM – 12:00 PM", Icons.Outlined.CenterFocusStrong, Cyan))
    }
    if (events.isNotEmpty() && hour < 17) {
        blocks.add(TimeBlock("${events.size} meeting${if (events.size > 1) "s" else ""} today", "Check calendar", Icons.Outlined.Groups, Pink))
    }
    if (priorityTasks.isNotEmpty()) {
        val topTask = priorityTasks.first().jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Top priority"
        blocks.add(TimeBlock(topTask, "Highest priority", Icons.Outlined.Star, Amber))
    }
    if (hour < 17) {
        blocks.add(TimeBlock("Afternoon work block", "1:00 PM – 5:00 PM", Icons.Outlined.WorkOutline, Green))
    }
    blocks.add(TimeBlock("End of day review", "Wrap up & reflect", Icons.Outlined.Summarize, Violet))

    return blocks
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private fun getBriefingTodayEvents(context: Context): List<Pair<String, String>> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    return try {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        val todayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val todayEnd = cal.timeInMillis

        val projection = arrayOf(CalendarContract.Events.DTSTART, CalendarContract.Events.TITLE)
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} < ?"
        val args = arrayOf(todayStart.toString(), todayEnd.toString())

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, args,
            "${CalendarContract.Events.DTSTART} ASC",
        ) ?: return emptyList()

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val events = mutableListOf<Pair<String, String>>()
        cursor.use {
            while (it.moveToNext()) {
                events.add(timeFormat.format(Date(it.getLong(0))) to (it.getString(1) ?: "Untitled"))
            }
        }
        events
    } catch (_: Exception) { emptyList() }
}
