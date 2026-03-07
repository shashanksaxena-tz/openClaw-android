package com.openclaw.android.ui.screens

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens ──────────────────────────────────────────────────────────────
private val BgBlack = Color(0xFF050508)
private val SurfaceLight = Color(0xFF16161D)
private val Violet = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
private val Amber = Color(0xFFF59E0B)
private val Red = Color(0xFFEF4444)
private val Green = Color(0xFF22C55E)
private val TextPrimary = Color(0xFFEEEEF0)
private val TextSecondary = Color(0xFF9CA3AF)
private val TextMuted = Color(0xFF6B7280)
private val GlassBg = Color(0xFF0D0D12).copy(alpha = 0.65f)
private val GlassBorder = Color.White.copy(alpha = 0.08f)
private val CardShape = RoundedCornerShape(16.dp)
private val PillShape = RoundedCornerShape(50)

@Serializable
private data class ScheduledNotification(
    val id: Int,
    val title: String,
    val message: String,
    val triggerTimeMs: Long,
    val repeatIntervalMs: Long = 0,
    val prompt: String = "",
)

@Composable
fun RemindersScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("smart_notifications", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var reminders by remember {
        mutableStateOf(loadReminders(prefs, json))
    }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val now = System.currentTimeMillis()
    val upcoming = reminders.filter { it.triggerTimeMs > now }.sortedBy { it.triggerTimeMs }
    val past = reminders.filter { it.triggerTimeMs <= now }.sortedByDescending { it.triggerTimeMs }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BgBlack,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgBlack)
        ) {
            // Header
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 2 },
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onBack),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Reminders",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                brush = Brush.linearGradient(listOf(Amber, Cyan)),
                            ),
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountPill("${upcoming.size} upcoming", Amber)
                        if (past.isNotEmpty()) CountPill("${past.size} past", TextMuted)
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (reminders.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.NotificationsNone, contentDescription = null, tint = Amber, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No reminders", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                Text("Ask AI to set a reminder for you", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(Brush.linearGradient(listOf(Amber, Cyan)))
                                        .clickable(onClick = onNavigateToChat)
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text("Set a reminder", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                                }
                            }
                        }
                    }
                }

                if (upcoming.isNotEmpty()) {
                    item {
                        Text("Upcoming", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary))
                    }
                    items(upcoming, key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            isPast = false,
                            onCancel = {
                                val updated = reminders.filter { it.id != reminder.id }
                                reminders = updated
                                saveReminders(prefs, json, updated)
                            },
                        )
                    }
                }

                if (past.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text("Past", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextMuted))
                    }
                    items(past.take(20), key = { it.id }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            isPast = true,
                            onCancel = {
                                val updated = reminders.filter { it.id != reminder.id }
                                reminders = updated
                                saveReminders(prefs, json, updated)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReminderCard(
    reminder: ScheduledNotification,
    isPast: Boolean,
    onCancel: () -> Unit,
) {
    val df = remember { SimpleDateFormat("EEE, MMM d 'at' h:mm a", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val isOverdue = !isPast && reminder.triggerTimeMs < now

    val repeatLabel = when {
        reminder.repeatIntervalMs >= 7L * 24 * 60 * 60 * 1000 -> "Weekly"
        reminder.repeatIntervalMs >= 24L * 60 * 60 * 1000 -> "Daily"
        reminder.repeatIntervalMs >= 60L * 60 * 1000 -> "Hourly"
        else -> null
    }

    val timeUntil = if (!isPast) {
        val diff = reminder.triggerTimeMs - now
        when {
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h"
            else -> "${diff / (24 * 60 * 60 * 1000)}d"
        }
    } else null

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(0.5.dp, if (isPast) GlassBorder else Amber.copy(alpha = 0.15f), CardShape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (isPast) TextMuted.copy(alpha = 0.12f)
                        else Amber.copy(alpha = 0.12f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPast) Icons.Outlined.NotificationsOff else Icons.Outlined.NotificationsActive,
                    contentDescription = null,
                    tint = if (isPast) TextMuted else Amber,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    reminder.title,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPast) TextMuted else TextPrimary,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (reminder.message.isNotBlank() && reminder.message != reminder.title) {
                    Text(
                        reminder.message,
                        style = TextStyle(fontSize = 12.sp, color = TextMuted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        df.format(Date(reminder.triggerTimeMs)),
                        style = TextStyle(fontSize = 11.sp, color = if (isPast) TextMuted else Amber),
                    )
                    if (repeatLabel != null) {
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(Violet.copy(alpha = 0.10f))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(repeatLabel, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium, color = Violet))
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (timeUntil != null) {
                    Text(timeUntil, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Amber))
                }
                Spacer(Modifier.height(4.dp))
                Icon(
                    Icons.Outlined.Cancel,
                    contentDescription = "Cancel",
                    tint = Red.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onCancel),
                )
            }
        }
    }
}

@Composable
private fun CountPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color))
    }
}

private fun loadReminders(prefs: android.content.SharedPreferences, json: Json): List<ScheduledNotification> {
    return try {
        val raw = prefs.getString("scheduled_list", "[]") ?: "[]"
        json.decodeFromString<List<ScheduledNotification>>(raw)
    } catch (_: Exception) { emptyList() }
}

private fun saveReminders(prefs: android.content.SharedPreferences, json: Json, reminders: List<ScheduledNotification>) {
    prefs.edit().putString("scheduled_list", json.encodeToString(reminders)).apply()
}
