package com.openclaw.android.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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
    onNavigateToHabits: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ── Offline detection ────────────────────────────────────────────────
    var isOnline by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) { isOnline = false }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm?.registerNetworkCallback(request, callback)
        // Check initial state
        isOnline = cm?.activeNetwork != null
        onDispose { cm?.unregisterNetworkCallback(callback) }
    }

    // ── Refresh key for pull-to-refresh ──────────────────────────────────
    var refreshKey by remember { mutableIntStateOf(0) }

    // Load data from SharedPreferences
    val taskPrefs = remember { context.getSharedPreferences("task_manager", Context.MODE_PRIVATE) }
    val notePrefs = remember { context.getSharedPreferences("smart_notes", Context.MODE_PRIVATE) }
    val teamPrefs = remember { context.getSharedPreferences("team_manager", Context.MODE_PRIVATE) }
    val insightPrefs = remember { context.getSharedPreferences("productivity_insights", Context.MODE_PRIVATE) }
    val decisionPrefs = remember { context.getSharedPreferences("decision_log", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    // Parse data with error tracking
    val tasksResult = remember(refreshKey) { parseJsonListSafe(taskPrefs.getString("tasks_data", "[]") ?: "[]", json) }
    val notesResult = remember(refreshKey) { parseJsonListSafe(notePrefs.getString("notes_data", "[]") ?: "[]", json) }
    val membersResult = remember(refreshKey) { parseJsonListSafe(teamPrefs.getString("members_data", "[]") ?: "[]", json) }
    val delegationsResult = remember(refreshKey) { parseJsonListSafe(teamPrefs.getString("delegations_data", "[]") ?: "[]", json) }
    val tasks = tasksResult.data
    val notes = notesResult.data
    val members = membersResult.data
    val delegations = delegationsResult.data

    // Collect any data errors
    val dataErrors = listOfNotNull(
        tasksResult.errorMessage,
        notesResult.errorMessage,
        membersResult.errorMessage,
        delegationsResult.errorMessage,
    )

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
    val tripsResult = remember(refreshKey) { parseJsonListSafe(travelPrefs.getString("trips_data", "[]") ?: "[]", json) }
    val trips = tripsResult.data
    val upcomingTrip = trips
        .filter { (it.optLong("startDate") ?: 0) > System.currentTimeMillis() }
        .minByOrNull { it.optLong("startDate") ?: Long.MAX_VALUE }

    // Reminders data
    val notifPrefs = remember { context.getSharedPreferences("smart_notifications", Context.MODE_PRIVATE) }
    val upcomingReminders = remember(refreshKey) {
        try {
            val raw = notifPrefs.getString("scheduled_list", "[]") ?: "[]"
            val arr = json.parseToJsonElement(raw)
            if (arr is kotlinx.serialization.json.JsonArray) {
                arr.mapNotNull { elem ->
                    val obj = elem as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                    val triggerMs = obj["triggerTimeMs"]?.let {
                        (it as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
                    } ?: return@mapNotNull null
                    if (triggerMs > System.currentTimeMillis()) {
                        SimpleJsonObj(obj.mapValues { (_, v) ->
                            when (v) {
                                is kotlinx.serialization.json.JsonPrimitive -> v.longOrNull ?: v.doubleOrNull ?: v.booleanOrNull ?: v.contentOrNull
                                else -> v.toString()
                            }
                        })
                    } else null
                }.sortedBy { it.optLong("triggerTimeMs") ?: Long.MAX_VALUE }.take(3)
            } else emptyList()
        } catch (_: Exception) { emptyList() }
    }

    // Calendar events
    val todayEvents = remember(refreshKey) { getTodayEvents(context) }

    // Time of day greeting
    val greeting = remember(refreshKey) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
    val dateStr = remember(refreshKey) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Refresh button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GlassBg)
                        .border(0.5.dp, GlassBorder, CircleShape)
                        .clickable { refreshKey++ },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TextSecondary, modifier = Modifier.size(20.dp))
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
        }

        Spacer(Modifier.height(16.dp))

        // ── Offline Banner ───────────────────────────────────────────
        OfflineBanner(isOnline = isOnline)

        // ── Data Error Banner ────────────────────────────────────────
        if (dataErrors.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmallCardShape)
                    .background(Red.copy(alpha = 0.10f))
                    .border(0.5.dp, Red.copy(alpha = 0.25f), SmallCardShape)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = "Data parsing errors", tint = Red, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Some data couldn't be loaded. Try asking AI to fix it.",
                    style = TextStyle(fontSize = 13.sp, color = Red),
                )
            }
            Spacer(Modifier.height(12.dp))
        }

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
        var showAllActions by remember { mutableStateOf(false) }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(600, 500)) + slideInVertically(tween(500, 500)) { it / 3 },
        ) {
            GlassSection(
                title = "Quick Actions",
                icon = Icons.Outlined.FlashOn,
                actionLabel = "See all",
                onAction = { showAllActions = true },
            ) {
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

        // ── All Actions Bottom Sheet ──────────────────────────────────────
        if (showAllActions) {
            QuickActionBottomSheet(
                onDismiss = { showAllActions = false },
                onNavigateToChat = onNavigateToChat,
                onNavigateToTasks = onNavigateToTasks,
                onNavigateToNotes = onNavigateToNotes,
                onNavigateToTeam = onNavigateToTeam,
                onNavigateToCalendar = onNavigateToCalendar,
                onNavigateToTravel = onNavigateToTravel,
                onNavigateToInsights = onNavigateToInsights,
                onNavigateToBriefing = onNavigateToBriefing,
                onNavigateToHabits = onNavigateToHabits,
                onNavigateToReminders = onNavigateToReminders,
                onNavigateToMemory = onNavigateToMemory,
                onNavigateToVoice = onNavigateToVoice,
                onNavigateToFiles = onNavigateToFiles,
                onNavigateToSettings = onNavigateToSettings,
            )
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

        // ── Upcoming Reminders ────────────────────────────────────────────
        if (upcomingReminders.isNotEmpty()) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(600, 650)) + slideInVertically(tween(500, 650)) { it / 3 },
            ) {
                GlassSection(
                    title = "Upcoming Reminders",
                    icon = Icons.Outlined.NotificationsActive,
                    actionLabel = "All",
                    onAction = onNavigateToReminders,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (reminder in upcomingReminders) {
                            val title = reminder.optString("title") ?: "Reminder"
                            val triggerMs = reminder.optLong("triggerTimeMs") ?: 0
                            val diff = triggerMs - System.currentTimeMillis()
                            val timeLabel = when {
                                diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m"
                                diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h"
                                else -> "${diff / (24 * 60 * 60 * 1000)}d"
                            }
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
                                        .background(Amber.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = Amber, modifier = Modifier.size(16.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    title,
                                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    "in $timeLabel",
                                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Amber),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }

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
private fun QuickActionChip(label: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
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

// ─── Quick Action Bottom Sheet ───────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickActionBottomSheet(
    onDismiss: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToTasks: () -> Unit,
    onNavigateToNotes: () -> Unit,
    onNavigateToTeam: () -> Unit,
    onNavigateToCalendar: () -> Unit,
    onNavigateToTravel: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToBriefing: () -> Unit,
    onNavigateToHabits: () -> Unit,
    onNavigateToReminders: () -> Unit,
    onNavigateToMemory: () -> Unit,
    onNavigateToVoice: () -> Unit,
    onNavigateToFiles: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0D0D12),
        scrimColor = Color.Black.copy(alpha = 0.5f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "All Actions",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    brush = VioletCyanGradient,
                ),
            )
            Spacer(Modifier.height(16.dp))

            // Communication
            ActionCategory("Communication", Cyan, listOf(
                QuickAction("AI Chat", Icons.Outlined.Chat, Cyan) to { onNavigateToChat(); onDismiss() },
                QuickAction("Voice Mode", Icons.Outlined.Mic, Cyan) to { onNavigateToVoice(); onDismiss() },
                QuickAction("Team", Icons.Outlined.Groups, Cyan) to { onNavigateToTeam(); onDismiss() },
            ))
            Spacer(Modifier.height(12.dp))

            // Productivity
            ActionCategory("Productivity", Violet, listOf(
                QuickAction("Tasks", Icons.Outlined.CheckCircle, Violet) to { onNavigateToTasks(); onDismiss() },
                QuickAction("Notes", Icons.Outlined.EditNote, Violet) to { onNavigateToNotes(); onDismiss() },
                QuickAction("Calendar", Icons.Outlined.CalendarMonth, Violet) to { onNavigateToCalendar(); onDismiss() },
                QuickAction("Habits", Icons.Outlined.FitnessCenter, Violet) to { onNavigateToHabits(); onDismiss() },
                QuickAction("Reminders", Icons.Outlined.NotificationsActive, Violet) to { onNavigateToReminders(); onDismiss() },
                QuickAction("Insights", Icons.Outlined.TrendingUp, Violet) to { onNavigateToInsights(); onDismiss() },
            ))
            Spacer(Modifier.height(12.dp))

            // Planning
            ActionCategory("Planning", Pink, listOf(
                QuickAction("Briefing", Icons.Outlined.Summarize, Pink) to { onNavigateToBriefing(); onDismiss() },
                QuickAction("Travel", Icons.Outlined.FlightTakeoff, Pink) to { onNavigateToTravel(); onDismiss() },
                QuickAction("Memory", Icons.Outlined.Psychology, Pink) to { onNavigateToMemory(); onDismiss() },
            ))
            Spacer(Modifier.height(12.dp))

            // Tools
            ActionCategory("Tools", Amber, listOf(
                QuickAction("Files", Icons.Outlined.Folder, Amber) to { onNavigateToFiles(); onDismiss() },
                QuickAction("Settings", Icons.Outlined.Settings, Amber) to { onNavigateToSettings(); onDismiss() },
            ))
        }
    }
}

@Composable
private fun ActionCategory(title: String, accent: Color, actions: List<Pair<QuickAction, () -> Unit>>) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accent, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(title, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
        }
        Spacer(Modifier.height(8.dp))
        // 3-column grid
        for (row in actions.chunked(3)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for ((action, onClick) in row) {
                    QuickActionChip(
                        label = action.label,
                        icon = action.icon,
                        color = action.color,
                        onClick = onClick,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
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

// ─── Offline Banner ──────────────────────────────────────────────────────────

@Composable
private fun OfflineBanner(isOnline: Boolean) {
    AnimatedVisibility(
        visible = !isOnline,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF59E0B).copy(alpha = 0.12f))
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.WifiOff, contentDescription = "No internet connection", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("You're offline \u2014 showing cached data", style = TextStyle(fontSize = 13.sp, color = Color(0xFFF59E0B)))
        }
    }
}

// ─── Data Helpers ────────────────────────────────────────────────────────────

private data class DataResult<T>(val data: T, val hasError: Boolean = false, val errorMessage: String? = null)

private data class SimpleJsonObj(val map: Map<String, Any?>) {
    fun optString(key: String): String? = map[key] as? String
    fun optLong(key: String): Long? = (map[key] as? Number)?.toLong()
}

private fun parseJsonListSafe(raw: String, json: Json): DataResult<List<SimpleJsonObj>> {
    return try {
        val arr = json.parseToJsonElement(raw).let {
            if (it is kotlinx.serialization.json.JsonArray) it else return DataResult(emptyList())
        }
        DataResult(arr.map { elem ->
            val obj = elem as? kotlinx.serialization.json.JsonObject ?: return@map SimpleJsonObj(emptyMap())
            SimpleJsonObj(obj.mapValues { (_, v) ->
                when (v) {
                    is kotlinx.serialization.json.JsonPrimitive -> {
                        v.longOrNull ?: v.doubleOrNull ?: v.booleanOrNull ?: v.contentOrNull
                    }
                    else -> v.toString()
                }
            })
        })
    } catch (e: Exception) {
        DataResult(emptyList(), hasError = true, errorMessage = "Could not load data: ${e.message?.take(50) ?: "unknown error"}")
    }
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
