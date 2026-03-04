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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val SmallCardShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(50)

@Composable
fun InsightsScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val json = remember { Json { ignoreUnknownKeys = true } }

    val insightPrefs = remember { context.getSharedPreferences("productivity_insights", Context.MODE_PRIVATE) }
    val taskPrefs = remember { context.getSharedPreferences("task_manager", Context.MODE_PRIVATE) }
    val notePrefs = remember { context.getSharedPreferences("smart_notes", Context.MODE_PRIVATE) }
    val teamPrefs = remember { context.getSharedPreferences("team_manager", Context.MODE_PRIVATE) }
    val decisionPrefs = remember { context.getSharedPreferences("decision_log", Context.MODE_PRIVATE) }

    // Parse daily logs
    val dailyLogs = remember {
        try {
            val raw = insightPrefs.getString("daily_logs", "{}") ?: "{}"
            val obj = json.parseToJsonElement(raw).jsonObject
            obj.entries.map { (date, log) ->
                date to log.jsonObject
            }.sortedByDescending { it.first }
        } catch (_: Exception) { emptyList() }
    }

    // Parse tasks
    val tasks = remember {
        try {
            val raw = taskPrefs.getString("tasks_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }

    // Parse notes
    val notes = remember {
        try {
            val raw = notePrefs.getString("notes_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }

    // Parse decisions
    val decisions = remember {
        try {
            val raw = decisionPrefs.getString("entries_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }

    // Parse delegations
    val delegations = remember {
        try {
            val raw = teamPrefs.getString("delegations_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }

    // Compute stats
    val totalTasks = tasks.size
    val completedTasks = tasks.count { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" }
    val totalNotes = notes.size
    val totalDecisions = decisions.count { it.jsonObject["type"]?.jsonPrimitive?.contentOrNull == "decision" }
    val completionRate = if (totalTasks > 0) (completedTasks * 100 / totalTasks) else 0
    val delegatedCount = delegations.size
    val selfCount = tasks.count {
        val assignedTo = it.jsonObject["assigned_to"]?.jsonPrimitive?.contentOrNull
            ?: it.jsonObject["assignedTo"]?.jsonPrimitive?.contentOrNull ?: ""
        assignedTo.isBlank()
    }
    val delegationRatio = if (totalTasks > 0) (delegatedCount * 100 / (delegatedCount + selfCount).coerceAtLeast(1)) else 0

    // Weekly stats from daily logs (last 7 days)
    val last7DaysLogs = dailyLogs.take(7)
    val avgFocusHours = if (last7DaysLogs.isNotEmpty()) {
        last7DaysLogs.mapNotNull { it.second["focusHours"]?.jsonPrimitive?.floatOrNull }.average().let { if (it.isNaN()) 0.0 else it }
    } else 0.0
    val avgMeetingHours = if (last7DaysLogs.isNotEmpty()) {
        last7DaysLogs.mapNotNull { it.second["meetingHours"]?.jsonPrimitive?.floatOrNull }.average().let { if (it.isNaN()) 0.0 else it }
    } else 0.0

    // Mood distribution
    val moodCounts = last7DaysLogs
        .mapNotNull { it.second["mood"]?.jsonPrimitive?.contentOrNull }
        .groupingBy { it }
        .eachCount()

    // Recent wins
    val recentWins = dailyLogs
        .mapNotNull { (date, log) -> log["keyWin"]?.jsonPrimitive?.contentOrNull?.let { date to it } }
        .take(5)

    // Tasks completed this week
    val weekStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    val tasksThisWeek = tasks.count {
        it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" &&
        (it.jsonObject["completedAt"]?.jsonPrimitive?.longOrNull ?: 0) > weekStart
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(BgBlack),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Header ───────────────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 2 },
            ) {
                Column {
                    Text(
                        "Insights",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(listOf(Green, Cyan)),
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Your productivity at a glance", style = TextStyle(fontSize = 14.sp, color = TextSecondary))
                }
            }
        }

        // ── Overview Cards ───────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 100)) + slideInVertically(tween(400, 100)) { it / 3 },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    InsightMetricCard(
                        value = "$completedTasks",
                        label = "Completed",
                        subtitle = "of $totalTasks tasks",
                        icon = Icons.Outlined.CheckCircle,
                        color = Green,
                        modifier = Modifier.weight(1f),
                    )
                    InsightMetricCard(
                        value = "$totalNotes",
                        label = "Notes",
                        subtitle = "captured",
                        icon = Icons.Outlined.EditNote,
                        color = Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    InsightMetricCard(
                        value = "$totalDecisions",
                        label = "Decisions",
                        subtitle = "logged",
                        icon = Icons.Outlined.Gavel,
                        color = Violet,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Completion Ring ──────────────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 200)) + slideInVertically(tween(400, 200)) { it / 3 },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CardShape)
                        .background(GlassBg)
                        .border(0.5.dp, GlassBorder, CardShape)
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Animated ring
                    val animatedProgress by animateFloatAsState(
                        targetValue = completionRate / 100f,
                        animationSpec = tween(1200, 300, easing = EaseOutCubic),
                        label = "ring-progress",
                    )

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .drawBehind {
                                // Background ring
                                drawArc(
                                    color = GlassBorder,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                                )
                                // Progress ring
                                drawArc(
                                    brush = Brush.sweepGradient(listOf(Green, Cyan, Green)),
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$completionRate%", style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Green))
                            Text("done", style = TextStyle(fontSize = 10.sp, color = TextMuted))
                        }
                    }

                    Spacer(Modifier.width(24.dp))

                    Column {
                        Text("Task Completion", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                        Spacer(Modifier.height(8.dp))
                        ProgressRow(label = "This week", value = "$tasksThisWeek tasks", color = Green)
                        Spacer(Modifier.height(4.dp))
                        ProgressRow(label = "Delegation", value = "$delegationRatio%", color = Violet)
                        Spacer(Modifier.height(4.dp))
                        ProgressRow(label = "Pending", value = "${totalTasks - completedTasks}", color = Amber)
                    }
                }
            }
        }

        // ── Weekly Focus & Meetings ──────────────────────────────────────
        item {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 300)) + slideInVertically(tween(400, 300)) { it / 3 },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FocusCard(
                        title = "Avg Focus",
                        value = String.format("%.1f", avgFocusHours),
                        unit = "hrs/day",
                        icon = Icons.Outlined.CenterFocusStrong,
                        color = Cyan,
                        modifier = Modifier.weight(1f),
                    )
                    FocusCard(
                        title = "Avg Meetings",
                        value = String.format("%.1f", avgMeetingHours),
                        unit = "hrs/day",
                        icon = Icons.Outlined.Groups,
                        color = Pink,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Mood Tracker ─────────────────────────────────────────────────
        if (moodCounts.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 400)) + slideInVertically(tween(400, 400)) { it / 3 },
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
                            Icon(Icons.Outlined.Mood, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Weekly Mood", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val moodEmoji = mapOf("great" to "Excellent", "good" to "Good", "okay" to "Okay", "low" to "Low", "stressed" to "Stressed")
                            val moodColors = mapOf("great" to Green, "good" to Cyan, "okay" to Amber, "low" to TextMuted, "stressed" to Red)
                            for ((mood, count) in moodCounts.entries.sortedByDescending { it.value }) {
                                val mColor = moodColors[mood] ?: TextMuted
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(SmallCardShape)
                                        .background(mColor.copy(alpha = 0.08f))
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("$count", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = mColor))
                                    Text(moodEmoji[mood] ?: mood, style = TextStyle(fontSize = 10.sp, color = TextMuted))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Daily Activity Bars ──────────────────────────────────────────
        if (last7DaysLogs.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 500)) + slideInVertically(tween(400, 500)) { it / 3 },
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
                            Icon(Icons.Outlined.BarChart, contentDescription = null, tint = Violet, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Daily Activity", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                        }
                        Spacer(Modifier.height(16.dp))

                        val maxTasks = last7DaysLogs.maxOfOrNull {
                            (it.second["tasksCompleted"]?.jsonPrimitive?.intOrNull ?: 0)
                        }?.coerceAtLeast(1) ?: 1

                        Row(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            for ((date, log) in last7DaysLogs.reversed()) {
                                val completed = log["tasksCompleted"]?.jsonPrimitive?.intOrNull ?: 0
                                val barFraction = completed.toFloat() / maxTasks
                                val animatedHeight by animateFloatAsState(
                                    targetValue = barFraction,
                                    animationSpec = tween(800, 500, easing = EaseOutCubic),
                                    label = "bar-$date",
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("$completed", style = TextStyle(fontSize = 10.sp, color = TextMuted))
                                    Spacer(Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .fillMaxHeight(animatedHeight.coerceAtLeast(0.05f))
                                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                            .background(
                                                Brush.verticalGradient(listOf(Violet, Cyan.copy(alpha = 0.6f)))
                                            ),
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        date.takeLast(2).trimStart('0'),
                                        style = TextStyle(fontSize = 10.sp, color = TextMuted),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Recent Wins ──────────────────────────────────────────────────
        if (recentWins.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 600)) + slideInVertically(tween(400, 600)) { it / 3 },
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
                            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Amber, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Recent Wins", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                        }
                        Spacer(Modifier.height(12.dp))
                        for ((date, win) in recentWins) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .size(6.dp)
                                        .background(Amber, CircleShape),
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(win, style = TextStyle(fontSize = 13.sp, color = TextPrimary), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(date, style = TextStyle(fontSize = 11.sp, color = TextMuted))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Decision Log Summary ─────────────────────────────────────────
        if (decisions.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 700)) + slideInVertically(tween(400, 700)) { it / 3 },
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
                                Icon(Icons.Outlined.Gavel, contentDescription = null, tint = Violet, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Decision Log", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                            }
                            Text("${decisions.size} entries", style = TextStyle(fontSize = 12.sp, color = TextMuted))
                        }
                        Spacer(Modifier.height(12.dp))

                        val typeGroups = decisions.groupBy {
                            it.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "other"
                        }
                        val typeColors = mapOf(
                            "decision" to Violet,
                            "reflection" to Cyan,
                            "lesson" to Green,
                            "journal" to Pink,
                            "what_worked" to Amber,
                            "what_didnt" to Red,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for ((type, entries) in typeGroups.entries.take(4)) {
                                val tColor = typeColors[type] ?: TextMuted
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(SmallCardShape)
                                        .background(tColor.copy(alpha = 0.08f))
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("${entries.size}", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = tColor))
                                        Text(type.replace("_", " "), style = TextStyle(fontSize = 9.sp, color = TextMuted), maxLines = 1)
                                    }
                                }
                            }
                        }

                        // Recent decisions
                        Spacer(Modifier.height(12.dp))
                        val recentDecisions = decisions
                            .sortedByDescending { it.jsonObject["createdAt"]?.jsonPrimitive?.longOrNull ?: 0 }
                            .take(3)
                        val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

                        for (decision in recentDecisions) {
                            val title = decision.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: ""
                            val type = decision.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: ""
                            val createdAt = decision.jsonObject["createdAt"]?.jsonPrimitive?.longOrNull ?: 0
                            val dColor = typeColors[type] ?: TextMuted

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(dColor.copy(alpha = 0.10f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(type.replace("_", " "), style = TextStyle(fontSize = 9.sp, color = dColor))
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(title, style = TextStyle(fontSize = 12.sp, color = TextPrimary), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(df.format(Date(createdAt)), style = TextStyle(fontSize = 10.sp, color = TextMuted))
                            }
                        }
                    }
                }
            }
        }

        // ── Empty State ──────────────────────────────────────────────────
        if (dailyLogs.isEmpty() && tasks.isEmpty() && decisions.isEmpty()) {
            item {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 200)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Outlined.TrendingUp, contentDescription = null, tint = Green, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No data yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                            Text("Start using tasks, notes, and logging to see insights", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                            Spacer(Modifier.height(16.dp))
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(Brush.linearGradient(listOf(Green, Cyan)))
                                    .clickable(onClick = onNavigateToChat)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                            ) {
                                Text("Log your first day", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun InsightMetricCard(
    value: String,
    label: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(GlassBg)
            .border(0.5.dp, GlassBorder, CardShape)
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
            Text(value, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary))
            Text(label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color))
            Text(subtitle, style = TextStyle(fontSize = 10.sp, color = TextMuted))
        }
    }
}

@Composable
private fun FocusCard(
    title: String,
    value: String,
    unit: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(GlassBg)
            .border(0.5.dp, GlassBorder, CardShape)
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary))
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = color))
                Spacer(Modifier.width(4.dp))
                Text(unit, style = TextStyle(fontSize = 12.sp, color = TextMuted), modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun ProgressRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = TextStyle(fontSize = 12.sp, color = TextMuted))
        Text(value, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color))
    }
}
