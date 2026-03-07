package com.openclaw.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Design tokens (consistent with app) ──────────────────────────────────────
private val BgBlack = Color(0xFF050508)
private val Violet = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
private val Pink = Color(0xFFEC4899)
private val Amber = Color(0xFFF59E0B)
private val TextPrimary = Color(0xFFEEEEF0)
private val TextSecondary = Color(0xFF9CA3AF)
private val TextMuted = Color(0xFF6B7280)
private val GlassBg = Color(0xFF0D0D12).copy(alpha = 0.65f)
private val GlassBorder = Color.White.copy(alpha = 0.08f)
private val CardShape = RoundedCornerShape(20.dp)
private val SmallCardShape = RoundedCornerShape(16.dp)
private val ItemCardShape = RoundedCornerShape(14.dp)

private val VioletCyanGradient = Brush.linearGradient(listOf(Violet, Cyan))

// ── Data model ───────────────────────────────────────────────────────────────

private data class DiscoverItem(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val viaChatPrompt: String? = null,
)

private data class DiscoverCategory(
    val title: String,
    val accent: Color,
    val items: List<DiscoverItem>,
)

// ─── Main Discover Screen ────────────────────────────────────────────────────

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
    onNavigateToTasks: () -> Unit = {},
    onNavigateToNotes: () -> Unit = {},
    onNavigateToTeam: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToTravel: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToBriefing: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToFiles: () -> Unit = {},
    onNavigateToHabits: () -> Unit = {},
    onNavigateToDecisions: () -> Unit = {},
    onNavigateToMemory: () -> Unit = {},
    onNavigateToVoice: () -> Unit = {},
    onNavigateToReminders: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    var searchQuery by remember { mutableStateOf("") }

    // Entrance animation
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Build categories
    val categories = remember(
        onNavigateToTasks, onNavigateToNotes, onNavigateToCalendar,
        onNavigateToBriefing, onNavigateToHabits, onNavigateToInsights,
        onNavigateToChat, onNavigateToVoice, onNavigateToTeam,
        onNavigateToTravel, onNavigateToDecisions, onNavigateToMemory,
        onNavigateToFiles, onNavigateToSettings, onNavigateToReminders,
    ) {
        listOf(
            DiscoverCategory(
                title = "Productivity",
                accent = Violet,
                items = listOf(
                    DiscoverItem("Tasks", Icons.Outlined.CheckCircle, onNavigateToTasks),
                    DiscoverItem("Notes", Icons.Outlined.EditNote, onNavigateToNotes),
                    DiscoverItem("Calendar", Icons.Outlined.CalendarMonth, onNavigateToCalendar),
                    DiscoverItem("Daily Briefing", Icons.Outlined.Summarize, onNavigateToBriefing),
                    DiscoverItem("Habits", Icons.Outlined.FitnessCenter, onNavigateToHabits),
                    DiscoverItem("Insights", Icons.Outlined.TrendingUp, onNavigateToInsights),
                ),
            ),
            DiscoverCategory(
                title = "Communication",
                accent = Cyan,
                items = listOf(
                    DiscoverItem("AI Chat", Icons.Outlined.Chat, onNavigateToChat),
                    DiscoverItem("Voice Mode", Icons.Outlined.Mic, onNavigateToVoice),
                    DiscoverItem("Team", Icons.Outlined.Groups, onNavigateToTeam),
                    DiscoverItem("Contacts", Icons.Outlined.Contacts, onNavigateToChat, viaChatPrompt = "Help me find a contact"),
                    DiscoverItem("SMS", Icons.Outlined.Sms, onNavigateToChat, viaChatPrompt = "Help me send a text message"),
                    DiscoverItem("Email", Icons.Outlined.Email, onNavigateToChat, viaChatPrompt = "Help me compose an email"),
                ),
            ),
            DiscoverCategory(
                title = "Planning",
                accent = Pink,
                items = listOf(
                    DiscoverItem("Travel", Icons.Outlined.FlightTakeoff, onNavigateToTravel),
                    DiscoverItem("Decisions", Icons.Outlined.Gavel, onNavigateToDecisions),
                    DiscoverItem("Memory", Icons.Outlined.Psychology, onNavigateToMemory),
                ),
            ),
            DiscoverCategory(
                title = "Tools",
                accent = Amber,
                items = listOf(
                    DiscoverItem("Files", Icons.Outlined.Folder, onNavigateToFiles),
                    DiscoverItem("Web Search", Icons.Outlined.TravelExplore, onNavigateToChat, viaChatPrompt = "Search the web for"),
                    DiscoverItem("Clipboard", Icons.Outlined.ContentPaste, onNavigateToChat, viaChatPrompt = "Help me with clipboard content"),
                    DiscoverItem("Reminders", Icons.Outlined.NotificationsActive, onNavigateToReminders),
                    DiscoverItem("Settings", Icons.Outlined.Settings, onNavigateToSettings),
                ),
            ),
        )
    }

    // Filter categories based on search query
    val filteredCategories = remember(searchQuery, categories) {
        if (searchQuery.isBlank()) {
            categories
        } else {
            categories.mapNotNull { category ->
                val filtered = category.items.filter {
                    it.label.contains(searchQuery, ignoreCase = true)
                }
                if (filtered.isEmpty()) null else category.copy(items = filtered)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgBlack)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // ── Header ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { -it / 2 },
        ) {
            Column {
                Text(
                    "Discover",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        brush = VioletCyanGradient,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "All your AI-powered tools in one place",
                    style = TextStyle(fontSize = 14.sp, color = TextSecondary),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Search Bar ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(500, 100)) + slideInVertically(tween(400, 100)) { it / 3 },
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Search tools...", color = TextMuted, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = TextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Clear",
                            tint = TextMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { searchQuery = "" },
                        )
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Violet,
                    focusedBorderColor = Violet.copy(alpha = 0.5f),
                    unfocusedBorderColor = GlassBorder,
                    focusedContainerColor = GlassBg,
                    unfocusedContainerColor = GlassBg,
                ),
                shape = SmallCardShape,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Category Sections ────────────────────────────────────────────
        filteredCategories.forEachIndexed { catIndex, category ->
            val delayBase = 200 + catIndex * 100
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(500, delayBase)) + slideInVertically(tween(400, delayBase)) { it / 3 },
            ) {
                CategorySection(category = category)
            }
            Spacer(Modifier.height(16.dp))
        }

        // Empty state
        if (filteredCategories.isEmpty()) {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(300)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.SearchOff,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No tools match \"$searchQuery\"",
                            style = TextStyle(fontSize = 14.sp, color = TextMuted),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Category Section ─────────────────────────────────────────────────────────

@Composable
private fun CategorySection(category: DiscoverCategory) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(GlassBg)
            .border(0.5.dp, GlassBorder, CardShape)
            .padding(16.dp),
    ) {
        // Category header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(category.accent, RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                category.title,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                ),
            )
        }

        Spacer(Modifier.height(14.dp))

        // 3-column grid
        val rows = category.items.chunked(3)
        rows.forEachIndexed { rowIndex, rowItems ->
            if (rowIndex > 0) Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowItems.forEach { item ->
                    DiscoverItemCard(
                        item = item,
                        accent = category.accent,
                        modifier = Modifier.weight(1f),
                    )
                }
                // Fill remaining slots in incomplete rows
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Individual Item Card ─────────────────────────────────────────────────────

@Composable
private fun DiscoverItemCard(
    item: DiscoverItem,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(ItemCardShape)
            .background(accent.copy(alpha = 0.06f))
            .border(0.5.dp, accent.copy(alpha = 0.12f), ItemCardShape)
            .clickable(onClick = item.onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = accent,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // "via AI Chat" chip for prompt-based items
        if (item.viaChatPrompt != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "via AI Chat",
                style = TextStyle(
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = accent.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
