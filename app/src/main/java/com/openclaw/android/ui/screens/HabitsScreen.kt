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
import kotlinx.serialization.json.*
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens ──────────────────────────────────────────────────────────────
private val BgBlack = Color(0xFF050508)
private val SurfaceLight = Color(0xFF16161D)
private val Violet = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
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
private val DialogShape = RoundedCornerShape(24.dp)
private val DialogBg = Color(0xFF12121A)

@Serializable
private data class Habit(
    val name: String,
    val description: String = "",
    val frequency: String = "daily",
    val createdAt: Long = System.currentTimeMillis(),
    val completions: List<Long> = emptyList(),
    val streak: Int = 0,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("habit_tracker", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var habits by remember {
        mutableStateOf(
            try {
                val raw = prefs.getString("habits_data", "{}") ?: "{}"
                json.decodeFromString<Map<String, Habit>>(raw)
            } catch (_: Exception) { emptyMap() }
        )
    }

    fun saveHabits(updated: Map<String, Habit>) {
        habits = updated
        prefs.edit().putString("habits_data", json.encodeToString(updated)).apply()
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val now = System.currentTimeMillis()
    val todayStart = now - (now % (24 * 60 * 60 * 1000))
    val sortedHabits = habits.values.sortedByDescending { it.streak }

    val completedToday = sortedHabits.count { h -> h.completions.any { it >= todayStart } }
    val totalHabits = sortedHabits.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BgBlack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = CircleShape,
                containerColor = Color.Transparent,
                modifier = Modifier
                    .size(56.dp)
                    .background(Brush.linearGradient(listOf(Violet, Cyan)), CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Habit", tint = Color.White)
            }
        },
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
                    Text(
                        "Habits",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(listOf(Green, Cyan)),
                        ),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("$completedToday/$totalHabits done today", Green)
                        val bestStreak = sortedHabits.maxOfOrNull { it.streak } ?: 0
                        if (bestStreak > 0) StatPill("Best streak: $bestStreak", Amber)
                    }
                }
            }

            // Habits list
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (sortedHabits.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = Green, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No habits yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                Text("Start tracking your daily routines", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(Brush.linearGradient(listOf(Green, Cyan)))
                                        .clickable(onClick = onNavigateToChat)
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text("Ask AI to create habits", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                                }
                            }
                        }
                    }
                }

                items(sortedHabits, key = { it.name }) { habit ->
                    val doneToday = habit.completions.any { it >= todayStart }
                    HabitCard(
                        habit = habit,
                        doneToday = doneToday,
                        onComplete = {
                            if (!doneToday) {
                                val updated = habits.toMutableMap()
                                val h = updated[habit.name] ?: return@HabitCard
                                val newCompletions = h.completions + System.currentTimeMillis()
                                val newStreak = calculateStreak(newCompletions, h.frequency)
                                updated[habit.name] = h.copy(completions = newCompletions, streak = newStreak)
                                saveHabits(updated)
                            }
                        },
                        onDelete = {
                            val updated = habits.toMutableMap()
                            updated.remove(habit.name)
                            saveHabits(updated)
                        },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateHabitDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, frequency ->
                val updated = habits.toMutableMap()
                updated[name] = Habit(name = name, description = description, frequency = frequency)
                saveHabits(updated)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    doneToday: Boolean,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val todayStart = now - (now % (24 * 60 * 60 * 1000))

    // Week visualization: last 7 days
    val weekDays = (6 downTo 0).map { daysAgo ->
        val dayStart = todayStart - daysAgo * 24 * 60 * 60 * 1000
        val dayEnd = dayStart + 24 * 60 * 60 * 1000
        habit.completions.any { it in dayStart until dayEnd }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(0.5.dp, if (doneToday) Green.copy(alpha = 0.25f) else GlassBorder, CardShape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Check-off circle
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(28.dp)
                    .then(
                        if (doneToday) Modifier.background(Green, CircleShape)
                        else Modifier
                            .border(2.dp, Green.copy(alpha = 0.5f), CircleShape)
                            .clickable(onClick = onComplete)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (doneToday) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        habit.name,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (doneToday) Green else TextPrimary,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = "Delete",
                        tint = TextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onDelete),
                    )
                }

                if (habit.description.isNotBlank()) {
                    Text(
                        habit.description,
                        style = TextStyle(fontSize = 12.sp, color = TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Streak and frequency
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (habit.streak > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LocalFireDepartment, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("${habit.streak}", style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Amber))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(Violet.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(habit.frequency, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Violet))
                    }
                    Text("${habit.completions.size} total", style = TextStyle(fontSize = 11.sp, color = TextMuted))
                }

                Spacer(Modifier.height(8.dp))

                // Week visualization
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = todayStart - 6 * 24 * 60 * 60 * 1000
                    weekDays.forEachIndexed { i, done ->
                        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                        val label = when (dayOfWeek) {
                            Calendar.MONDAY -> "M"; Calendar.TUESDAY -> "T"; Calendar.WEDNESDAY -> "W"
                            Calendar.THURSDAY -> "T"; Calendar.FRIDAY -> "F"; Calendar.SATURDAY -> "S"
                            else -> "S"
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, style = TextStyle(fontSize = 8.sp, color = TextMuted))
                            Spacer(Modifier.height(2.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (done) Green.copy(alpha = 0.7f)
                                        else GlassBg
                                    ),
                            )
                        }
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color))
    }
}

@Composable
private fun CreateHabitDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, frequency: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var frequency by remember { mutableStateOf("daily") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        shape = DialogShape,
        title = {
            Text(
                "Create Habit",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(listOf(Green, Cyan)),
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit name", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Green,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = Green,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Green,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Green,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = Green,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Green,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                Text("Frequency", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("daily" to Green, "weekly" to Violet).forEach { (freq, color) ->
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(if (frequency == freq) color.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = if (frequency == freq) 1.dp else 0.5.dp,
                                    color = if (frequency == freq) color.copy(alpha = 0.6f) else GlassBorder,
                                    shape = PillShape,
                                )
                                .clickable { frequency = freq }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                freq.replaceFirstChar { it.uppercase() },
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = if (frequency == freq) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (frequency == freq) color else TextSecondary,
                                ),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(
                        if (name.isNotBlank()) Brush.linearGradient(listOf(Green, Cyan))
                        else Brush.linearGradient(listOf(TextMuted.copy(alpha = 0.3f), TextMuted.copy(alpha = 0.3f)))
                    )
                    .clickable(enabled = name.isNotBlank()) { onCreate(name.trim(), description.trim(), frequency) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("Create", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .border(0.5.dp, GlassBorder, PillShape)
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("Cancel", style = TextStyle(fontSize = 14.sp, color = TextSecondary))
            }
        },
    )
}

private fun calculateStreak(completions: List<Long>, frequency: String): Int {
    if (completions.isEmpty()) return 0
    val sorted = completions.sorted()
    val interval = if (frequency == "weekly") 7L * 24 * 60 * 60 * 1000 else 24L * 60 * 60 * 1000
    val tolerance = interval + interval / 2

    var streak = 1
    for (i in sorted.size - 1 downTo 1) {
        val gap = sorted[i] - sorted[i - 1]
        if (gap <= tolerance) streak++ else break
    }
    return streak
}
