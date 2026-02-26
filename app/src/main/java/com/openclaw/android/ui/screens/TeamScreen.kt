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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@Composable
fun TeamScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("team_manager", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    val members = remember {
        try {
            val raw = prefs.getString("members_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }
    val delegations = remember {
        try {
            val raw = prefs.getString("delegations_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }
    val performanceNotes = remember {
        try {
            val raw = prefs.getString("notes_data", "[]") ?: "[]"
            json.parseToJsonElement(raw).jsonArray.toList()
        } catch (_: Exception) { emptyList() }
    }

    val activeDelegations = delegations.count { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "assigned" }
    val completedDelegations = delegations.count { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" }
    val overdueDelegations = delegations.count {
        it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "assigned" &&
        (it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE) < System.currentTimeMillis()
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Column(modifier = modifier.fillMaxSize().background(BgBlack)) {
        // ── Header ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 2 },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    "Team",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        brush = Brush.linearGradient(listOf(Pink, Violet)),
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text("${members.size} members", style = TextStyle(fontSize = 14.sp, color = TextSecondary))
            }
        }

        // ── Dashboard Stats ──────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400, 100)) + slideInVertically(tween(400, 100)) { it / 3 },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TeamStatCard(value = "$activeDelegations", label = "Active", color = Violet, modifier = Modifier.weight(1f))
                TeamStatCard(value = "$completedDelegations", label = "Done", color = Green, modifier = Modifier.weight(1f))
                TeamStatCard(value = "$overdueDelegations", label = "Overdue", color = Red, modifier = Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Tab Switcher ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(400, 150)),
        ) {
            val tabs = listOf("Members", "Delegations", "Activity")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs.size) { index ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .clip(PillShape)
                            .background(if (isSelected) Violet.copy(alpha = 0.15f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) Violet.copy(alpha = 0.5f) else GlassBorder,
                                shape = PillShape,
                            )
                            .clickable { selectedTab = index }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(
                            tabs[index],
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) Violet else TextSecondary,
                            ),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Content ──────────────────────────────────────────────────────
        when (selectedTab) {
            0 -> MembersTab(members, delegations, performanceNotes, onNavigateToChat)
            1 -> DelegationsTab(delegations, members)
            2 -> ActivityTab(performanceNotes, members)
        }
    }
}

@Composable
private fun MembersTab(
    members: List<JsonElement>,
    delegations: List<JsonElement>,
    notes: List<JsonElement>,
    onNavigateToChat: () -> Unit,
) {
    if (members.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Groups, contentDescription = null, tint = Pink, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("No team members yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                Text("Ask AI to add team members", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Brush.linearGradient(listOf(Pink, Violet)))
                        .clickable(onClick = onNavigateToChat)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Add team member via AI", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                }
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(members, key = { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: "" }) { member ->
            MemberCard(member, delegations, notes)
        }
    }
}

@Composable
private fun MemberCard(member: JsonElement, delegations: List<JsonElement>, notes: List<JsonElement>) {
    val name = member.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
    val role = member.jsonObject["role"]?.jsonPrimitive?.contentOrNull ?: ""
    val memberId = member.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
    val strengths = try {
        member.jsonObject["strengths"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    } catch (_: Exception) { emptyList() }
    val goals = try {
        member.jsonObject["goals"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    val memberDelegations = delegations.count { it.jsonObject["memberId"]?.jsonPrimitive?.contentOrNull == memberId && it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "assigned" }
    val memberNotes = notes.count { it.jsonObject["memberId"]?.jsonPrimitive?.contentOrNull == memberId }

    val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
    val lastNote = notes.filter { it.jsonObject["memberId"]?.jsonPrimitive?.contentOrNull == memberId }
        .maxByOrNull { it.jsonObject["date"]?.jsonPrimitive?.longOrNull ?: 0 }
    val needsCheckIn = lastNote == null || (lastNote.jsonObject["date"]?.jsonPrimitive?.longOrNull ?: 0) < weekAgo

    // Color from name hash
    val colors = listOf(Violet, Cyan, Pink, Green, Amber)
    val avatarColor = colors[name.hashCode().mod(colors.size).let { if (it < 0) it + colors.size else it }]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmallCardShape)
            .background(SurfaceLight)
            .border(
                width = 0.5.dp,
                color = if (needsCheckIn) Amber.copy(alpha = 0.3f) else GlassBorder,
                shape = SmallCardShape,
            )
            .padding(16.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .drawBehind {
                            if (needsCheckIn) {
                                drawCircle(color = Amber.copy(alpha = 0.2f), radius = size.minDimension * 0.7f)
                            }
                        }
                        .background(avatarColor.copy(alpha = 0.15f), CircleShape)
                        .border(1.5.dp, avatarColor.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        name.take(2).uppercase(),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = avatarColor),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                        if (needsCheckIn) {
                            Spacer(Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(Amber.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text("Check-in due", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Amber))
                            }
                        }
                    }
                    Text(role, style = TextStyle(fontSize = 13.sp, color = TextMuted))
                }

                // Stats badges
                Column(horizontalAlignment = Alignment.End) {
                    if (memberDelegations > 0) {
                        Text("$memberDelegations tasks", style = TextStyle(fontSize = 11.sp, color = Violet))
                    }
                    Text("$memberNotes notes", style = TextStyle(fontSize = 11.sp, color = TextMuted))
                }
            }

            // Strengths
            if (strengths.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (s in strengths.take(3)) {
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(Green.copy(alpha = 0.08f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(s, style = TextStyle(fontSize = 10.sp, color = Green))
                        }
                    }
                    if (strengths.size > 3) {
                        Text("+${strengths.size - 3}", style = TextStyle(fontSize = 10.sp, color = TextMuted))
                    }
                }
            }

            // Goals
            if (goals.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.Flag, contentDescription = null, tint = Cyan.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                    Text(
                        goals.joinToString(" | "),
                        style = TextStyle(fontSize = 11.sp, color = TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DelegationsTab(delegations: List<JsonElement>, members: List<JsonElement>) {
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val active = delegations.filter { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "assigned" }
        .sortedBy { it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE }

    if (active.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.AssignmentTurnedIn, contentDescription = null, tint = Green, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("No active delegations", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(active) { del ->
            val memberId = del.jsonObject["memberId"]?.jsonPrimitive?.contentOrNull ?: ""
            val memberName = members.find { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull == memberId }
                ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown"
            val task = del.jsonObject["task"]?.jsonPrimitive?.contentOrNull ?: ""
            val deadline = del.jsonObject["deadline"]?.jsonPrimitive?.longOrNull
            val isOverdue = deadline != null && deadline < System.currentTimeMillis()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmallCardShape)
                    .background(SurfaceLight)
                    .border(0.5.dp, if (isOverdue) Red.copy(alpha = 0.3f) else GlassBorder, SmallCardShape)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(36.dp).background(Violet.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(memberName.take(1).uppercase(), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Violet))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(task, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary), maxLines = 2)
                        Text(memberName, style = TextStyle(fontSize = 12.sp, color = TextMuted))
                    }
                    if (deadline != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(df.format(Date(deadline)), style = TextStyle(fontSize = 11.sp, color = if (isOverdue) Red else TextMuted))
                            if (isOverdue) Text("OVERDUE", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Red))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityTab(notes: List<JsonElement>, members: List<JsonElement>) {
    val df = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val recentNotes = notes.sortedByDescending { it.jsonObject["date"]?.jsonPrimitive?.longOrNull ?: 0 }

    if (recentNotes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = Cyan, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("No activity yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(recentNotes.take(20)) { note ->
            val memberId = note.jsonObject["memberId"]?.jsonPrimitive?.contentOrNull ?: ""
            val memberName = members.find { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull == memberId }
                ?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown"
            val type = note.jsonObject["type"]?.jsonPrimitive?.contentOrNull ?: "note"
            val content = note.jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
            val date = note.jsonObject["date"]?.jsonPrimitive?.longOrNull ?: 0

            val typeColor = when (type) {
                "praise" -> Green; "concern" -> Red; "coaching" -> Cyan
                "performance" -> Violet; "feedback" -> Amber; else -> TextMuted
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmallCardShape)
                    .background(SurfaceLight)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(typeColor),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(memberName, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                Box(
                                    modifier = Modifier.clip(PillShape).background(typeColor.copy(alpha = 0.12f)).padding(horizontal = 6.dp, vertical = 1.dp),
                                ) {
                                    Text(type, style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = typeColor))
                                }
                            }
                            Text(df.format(Date(date)), style = TextStyle(fontSize = 10.sp, color = TextMuted))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(content, style = TextStyle(fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamStatCard(value: String, label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(SmallCardShape)
            .background(color.copy(alpha = 0.08f))
            .border(0.5.dp, color.copy(alpha = 0.15f), SmallCardShape)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color))
            Text(label, style = TextStyle(fontSize = 11.sp, color = TextMuted))
        }
    }
}
