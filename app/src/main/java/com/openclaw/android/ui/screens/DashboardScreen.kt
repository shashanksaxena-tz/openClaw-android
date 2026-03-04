package com.openclaw.android.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens (consistent with app) ──────────────────────────────────────
private val BgBlack = Color(0xFF050508)
private val Surface = Color(0xFF0D0D12)
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
private val CardShape = RoundedCornerShape(20.dp)
private val SmallCardShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)

private val VioletCyanGradient = Brush.linearGradient(listOf(Violet, Cyan))
private val VioletPinkGradient = Brush.linearGradient(listOf(Violet, Pink))

// ─── Main Dashboard Screen ───────────────────────────────────────────────────

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToTeam: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToTravel: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToBriefing: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Load data from SharedPreferences
    val taskPrefs = remember { context.getSharedPreferences("task_manager", Context.MODE_PRIVATE) }
    val notePrefs = remember { context.getSharedPreferences("smart_notes", Context.MODE_PRIVATE) }
    val teamPrefs = remember { context.getSharedPreferences("team_manager", Context.MODE_PRIVATE) }
    val insightPrefs = remember { context.getSharedPreferences("productivity_insights", Context.MODE_PRIVATE) }
    val decisionPrefs = remember { context.getSharedPreferences("decision_log", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    // Parse data
    val tasks = remember { parseJsonList(taskPrefs.getString("tasks_data", "[]") ?: "[]", json) }
    val notes = remember { parseJsonList(notePrefs.getString("notes_data", "[]") ?: "[]", json) }
    val members = remember { parseJsonList(teamPrefs.getString("members_data", "[]") ?: "[]", json) }
    val delegations = remember { parseJsonList(teamPrefs.getString("delegations_data", "[]") ?: "[]", json) }

    val pendingTasks = tasks.count { it.optString("status") != "done" }
    val highPriorityTasks = tasks.count { it.optString("status") != "done" && it.optString("priority") == "high" }
    val overdueTasks = tasks.count {
        it.optString("status") != "done" &&
        (it.optLong("deadline") ?: Long.MAX_VALUE) < System.currentTimeMillis()
    }
    val completedToday = run {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }.timeInMillis
        tasks.count { it.optString("status") == "done" && (it.optLong("completedAt") ?: 0) > todayStart }
    }
    val activeDelegations = delegations.count { it.optString("status") == "assigned" }

    // Travel data
    val travelPrefs = remember { context.getSharedPreferences("travel_manager", Context.MODE_PRIVATE) }
    val trips = remember { parseJsonList(travelPrefs.getString("trips_data", "[]") ?: "[]", json) }
    val upcomingTrip = trips
        .filter { (it.optLong("startDate") ?: 0) > System.currentTimeMillis() }
        .minByOrNull { it.optLong("startDate") ?: Long.MAX_VALUE }

    // Calendar events
    val todayEvents = remember { getTodayEvents(context) }

    // Time of day greeting
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateStr = remember {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date())
    }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -it / 2 },
                ) {
                    Text(
                        greeting,
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            brush = VioletCyanGradient,
                        ),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(dateStr, style = TextStyle(fontSize = 14.sp, color = TextSecondary))
            }
            // Settings gear
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(GlassBg)
                    .border(0.5.dp, GlassBorder, CircleShape)
                    .clickable(onClick = onNavigateToSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = TextSecondary, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Daily Briefing Banner ─────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500, 50)) + slideInVertically(tween(400, 50)) { it / 3 },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmallCardShape)
                    .background(Brush.linearGradient(listOf(Amber.copy(alpha = 0.12f), Pink.copy(alpha = 0.08f))))
                    .border(0.5.dp, Amber.copy(alpha = 0.20f), SmallCardShape)
                    .clickable(onClick = onNavigateToBriefing)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Amber.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Summarize, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Daily Briefing", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                        Text("Your priorities, schedule & action plan", style = TextStyle(fontSize = 12.sp, color = TextMuted))
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Amber.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Quick Stats Row ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 100)) + slideInVertically(tween(500, 100)) { it / 3 },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    value = "$pendingTasks",
                    label = "Tasks",
                    icon = Icons.Outlined.CheckCircle,
                    color = Violet,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToTasks,
                )
                StatCard(
                    value = "${todayEvents.size}",
                    label = "Events",
                    icon = Icons.Outlined.CalendarMonth,
                    color = Cyan,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToCalendar,
                )
                StatCard(
                    value = "${members.size}",
                    label = "Team",
                    icon = Icons.Outlined.Groups,
                    color = Pink,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToTeam,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Priority Alerts ──────────────────────────────────────────────
        if (highPriorityTasks > 0 || overdueTasks > 0) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, 200)) + slideInVertically(tween(500, 200)) { it / 3 },
            ) {
                Column {
                    if (overdueTasks > 0) {
                        AlertBanner(
                            text = "$overdueTasks overdue task${if (overdueTasks > 1) "s" else ""} need attention",
                            color = Red,
                            icon = Icons.Filled.Warning,
                            onClick = onNavigateToTasks,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (highPriorityTasks > 0) {
                        AlertBanner(
                            text = "$highPriorityTasks high-priority task${if (highPriorityTasks > 1) "s" else ""} pending",
                            color = Amber,
                            icon = Icons.Filled.PriorityHigh,
                            onClick = onNavigateToTasks,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }

        // ── Today's Calendar ─────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 300)) + slideInVertically(tween(500, 300)) { it / 3 },
        ) {
            GlassSection(title = "Today's Schedule", icon = Icons.Outlined.CalendarMonth, actionLabel = "Full calendar", onAction = onNavigateToCalendar) {
                if (todayEvents.isEmpty()) {
                    EmptyCard("No events today — great for focus work!")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((time, title) in todayEvents.take(5)) {
                            EventRow(time = time, title = title)
                        }
                        if (todayEvents.size > 5) {
                            Text(
                                "+${todayEvents.size - 5} more",
                                style = TextStyle(fontSize = 12.sp, color = Violet),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Top Priority Tasks ───────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 400)) + slideInVertically(tween(500, 400)) { it / 3 },
        ) {
            GlassSection(
                title = "Priority Tasks",
                icon = Icons.Outlined.CheckCircle,
                actionLabel = "See all",
                onAction = onNavigateToTasks,
            ) {
                val priorityTasks = tasks
                    .filter { it.optString("status") != "done" }
                    .sortedWith(compareBy<SimpleJsonObj> {
                        when (it.optString("priority")) { "high" -> 0; "medium" -> 1; else -> 2 }
                    }.thenBy { it.optLong("deadline") ?: Long.MAX_VALUE })
                    .take(4)

                if (priorityTasks.isEmpty()) {
                    EmptyCard("No pending tasks. You're all caught up!")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (task in priorityTasks) {
                            TaskRow(
                                title = task.optString("title") ?: "Untitled",
                                priority = task.optString("priority") ?: "medium",
                                project = task.optString("project") ?: "",
                                deadline = task.optLong("deadline"),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Quick Actions ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 500)) + slideInVertically(tween(500, 500)) { it / 3 },
        ) {
            GlassSection(title = "Quick Actions", icon = Icons.Outlined.FlashOn) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(quickActions) { action ->
                        QuickActionChip(
                            label = action.label,
                            icon = action.icon,
                            color = action.color,
                            onClick = {
                                when (action.label) {
                                    "New Task" -> onNavigateToTasks()
                                    "New Note" -> onNavigateToNotes()
                                    "Ask AI" -> onNavigateToChat()
                                    "Team" -> onNavigateToTeam()
                                    "Briefing" -> onNavigateToBriefing()
                                    "Calendar" -> onNavigateToCalendar()
                                    "Travel" -> onNavigateToTravel()
                                    "Insights" -> onNavigateToInsights()
                                    else -> onNavigateToChat()
                                }
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Recent Notes ─────────────────────────────────────────────────
        if (notes.isNotEmpty()) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, 600)) + slideInVertically(tween(500, 600)) { it / 3 },
            ) {
                GlassSection(
                    title = "Recent Notes",
                    icon = Icons.Outlined.EditNote,
                    actionLabel = "See all",
                    onAction = onNavigateToNotes,
                ) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(notes.sortedByDescending { it.optLong("createdAt") ?: 0 }.take(5)) { note ->
                            NotePreviewCard(
                                content = note.optString("content") ?: "",
                                category = note.optString("category") ?: "personal",
                                createdAt = note.optLong("createdAt") ?: 0,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Team Delegations ─────────────────────────────────────────────
        if (activeDelegations > 0) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, 700)) + slideInVertically(tween(500, 700)) { it / 3 },
            ) {
                GlassSection(
                    title = "Active Delegations",
                    icon = Icons.Outlined.AssignmentInd,
                    actionLabel = "Team",
                    onAction = onNavigateToTeam,
                ) {
                    val activeDels = delegations.filter { it.optString("status") == "assigned" }.take(3)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (del in activeDels) {
                            val memberId = del.optString("memberId") ?: ""
                            val memberName = members.find { it.optString("id") == memberId }
                                ?.optString("name") ?: "Unknown"
                            DelegationRow(
                                task = del.optString("task") ?: "Untitled",
                                person = memberName,
                                deadline = del.optLong("deadline"),
                            )
                        }
                    }
                }
            }
        }

        // ── Upcoming Travel ─────────────────────────────────────────────
        if (upcomingTrip != null) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, 750)) + slideInVertically(tween(500, 750)) { it / 3 },
            ) {
                val tripName = upcomingTrip.optString("name") ?: "Trip"
                val dest = upcomingTrip.optString("destination") ?: ""
                val startDate = upcomingTrip.optLong("startDate") ?: 0
                val daysUntil = ((startDate - System.currentTimeMillis()) / 86400000).toInt().coerceAtLeast(0)
                val df = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())

                GlassSection(
                    title = "Upcoming Travel",
                    icon = Icons.Outlined.FlightTakeoff,
                    actionLabel = "All trips",
                    onAction = onNavigateToTravel,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmallCardShape)
                            .background(Amber.copy(alpha = 0.06f))
                            .border(0.5.dp, Amber.copy(alpha = 0.12f), SmallCardShape)
                            .clickable(onClick = onNavigateToTravel)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Amber.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.FlightTakeoff, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tripName, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                            if (dest.isNotBlank()) Text(dest, style = TextStyle(fontSize = 12.sp, color = TextSecondary))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$daysUntil", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Amber))
                            Text("days", style = TextStyle(fontSize = 10.sp, color = TextMuted))
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        // ── Productivity Summary ─────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 800)) + slideInVertically(tween(500, 800)) { it / 3 },
        ) {
            GlassSection(title = "Today's Progress", icon = Icons.Outlined.TrendingUp, actionLabel = "Insights", onAction = onNavigateToInsights) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MiniStatCard(
                        value = "$completedToday",
                        label = "Completed",
                        color = Green,
                        modifier = Modifier.weight(1f),
                    )
                    MiniStatCard(
                        value = "${notes.count { (it.optLong("createdAt") ?: 0) > Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0) }.timeInMillis }}",
                        label = "Notes",
                        color = Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    MiniStatCard(
                        value = "$activeDelegations",
                        label = "Delegated",
                        color = Violet,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ─── Sub-Components ──────────────────────────────────────────────────────────

@Composable
private fun GlassSection(
    title: String,
    icon: ImageVector,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Violet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
            }
            if (actionLabel != null && onAction != null) {
                Text(
                    actionLabel,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Violet),
                    modifier = Modifier.clickable(onClick = onAction),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .clip(SmallCardShape)
            .background(GlassBg)
            .border(0.5.dp, GlassBorder, SmallCardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextPrimary))
            Text(label, style = TextStyle(fontSize = 12.sp, color = TextMuted))
        }
    }
}

@Composable
private fun MiniStatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(SmallCardShape)
            .background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.15f), SmallCardShape)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color))
            Text(label, style = TextStyle(fontSize = 11.sp, color = TextMuted))
        }
    }
}

@Composable
private fun AlertBanner(text: String, color: Color, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(color.copy(alpha = 0.10f))
            .border(0.5.dp, color.copy(alpha = 0.25f), SmallCardShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color), modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = color.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun EventRow(time: String, title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(SurfaceLight)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Cyan),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(time, style = TextStyle(fontSize = 12.sp, color = TextMuted))
        }
    }
}

@Composable
private fun TaskRow(title: String, priority: String, project: String, deadline: Long?) {
    val priorityColor = when (priority) { "high" -> Red; "medium" -> Amber; else -> TextMuted }
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(SurfaceLight)
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
            Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (project.isNotBlank()) Text(project, style = TextStyle(fontSize = 11.sp, color = TextMuted))
        }
        if (deadline != null) {
            val isOverdue = deadline < System.currentTimeMillis()
            Text(
                df.format(Date(deadline)),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (isOverdue) Red else TextMuted),
            )
        }
    }
}

@Composable
private fun DelegationRow(task: String, person: String, deadline: Long?) {
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(SurfaceLight)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Violet.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                person.firstOrNull()?.uppercase() ?: "?",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Violet),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(task, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(person, style = TextStyle(fontSize = 11.sp, color = TextMuted))
        }
        if (deadline != null) {
            Text(df.format(Date(deadline)), style = TextStyle(fontSize = 11.sp, color = TextMuted))
        }
    }
}

@Composable
private fun NotePreviewCard(content: String, category: String, createdAt: Long) {
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val categoryColor = when (category) {
        "meeting" -> Cyan; "task" -> Violet; "business_idea" -> Pink
        "travel" -> Amber; "decision" -> Green; else -> TextMuted
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .height(120.dp)
            .clip(SmallCardShape)
            .background(SurfaceLight)
            .border(0.5.dp, GlassBorder, SmallCardShape)
            .padding(12.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(categoryColor.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(category, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = categoryColor))
                }
                Spacer(Modifier.weight(1f))
                Text(df.format(Date(createdAt)), style = TextStyle(fontSize = 10.sp, color = TextMuted))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                content,
                style = TextStyle(fontSize = 12.sp, color = TextSecondary, lineHeight = 16.sp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QuickActionChip(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.10f))
            .border(0.5.dp, color.copy(alpha = 0.20f), PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = color))
        }
    }
}

@Composable
private fun EmptyCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(SurfaceLight)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = TextStyle(fontSize = 13.sp, color = TextMuted))
    }
}

// ─── Quick Actions Data ──────────────────────────────────────────────────────

private data class QuickAction(val label: String, val icon: ImageVector, val color: Color)

private val quickActions = listOf(
    QuickAction("Briefing", Icons.Outlined.Summarize, Amber),
    QuickAction("Calendar", Icons.Outlined.CalendarMonth, Cyan),
    QuickAction("New Task", Icons.Outlined.AddTask, Violet),
    QuickAction("New Note", Icons.Outlined.EditNote, Green),
    QuickAction("Travel", Icons.Outlined.FlightTakeoff, Pink),
    QuickAction("Insights", Icons.Outlined.TrendingUp, Green),
    QuickAction("Ask AI", Icons.Outlined.SmartToy, Pink),
    QuickAction("Team", Icons.Outlined.Groups, Violet),
)

// ─── Data Helpers ────────────────────────────────────────────────────────────

private data class SimpleJsonObj(val map: Map<String, Any?>) {
    fun optString(key: String): String? = map[key] as? String
    fun optLong(key: String): Long? = (map[key] as? Number)?.toLong()
}

private fun parseJsonList(raw: String, json: Json): List<SimpleJsonObj> {
    return try {
        val arr = json.parseToJsonElement(raw).let {
            if (it is kotlinx.serialization.json.JsonArray) it else return emptyList()
        }
        arr.map { elem ->
            val obj = elem as? kotlinx.serialization.json.JsonObject ?: return@map SimpleJsonObj(emptyMap())
            SimpleJsonObj(obj.mapValues { (_, v) ->
                when (v) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        v.longOrNull ?: v.doubleOrNull ?: v.booleanOrNull ?: v.contentOrNull
                    }
                    else -> v.toString()
                }
            })
        }
    } catch (_: Exception) { emptyList() }
}

private fun getTodayEvents(context: Context): List<Pair<String, String>> {
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
        val selectionArgs = arrayOf(todayStart.toString(), todayEnd.toString())

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC"
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
