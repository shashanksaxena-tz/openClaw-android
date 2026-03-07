package com.openclaw.android.ui.screens

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.data.MemorySystem
import com.openclaw.android.data.db.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Design tokens ──────────────────────────────────────────────────────────────
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
private val DialogShape = RoundedCornerShape(24.dp)
private val DialogBg = Color(0xFF12121A)

private val categoryColors = mapOf(
    "preference" to Violet,
    "fact" to Cyan,
    "person" to Pink,
    "habit" to Green,
    "note" to Amber,
)

@Composable
fun MemoryScreen(
    memorySystem: MemorySystem,
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var memories by remember { mutableStateOf<List<MemoryEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<MemoryEntity?>(null) }

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        withContext(Dispatchers.IO) {
            memories = memorySystem.getAll()
        }
        isLoading = false
    }

    val categories = memories.map { it.category }.distinct()
    val filteredMemories = if (selectedCategory != null) {
        memories.filter { it.category == selectedCategory }
    } else memories

    val grouped = filteredMemories.groupBy { it.category }

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
                    Text(
                        "About Me",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(listOf(Violet, Pink)),
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "What the AI remembers about you",
                        style = TextStyle(fontSize = 14.sp, color = TextSecondary),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatPill("${memories.size} memories", Violet)
                        StatPill("${categories.size} categories", Cyan)
                    }
                }
            }

            // Category filter
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 100)),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        FilterPill(
                            label = "All",
                            selected = selectedCategory == null,
                            color = Violet,
                            onClick = { selectedCategory = null },
                        )
                    }
                    items(categories) { cat ->
                        FilterPill(
                            label = cat.replaceFirstChar { it.uppercase() },
                            selected = selectedCategory == cat,
                            color = categoryColors[cat] ?: TextMuted,
                            count = memories.count { it.category == cat },
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Violet, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (filteredMemories.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.Psychology, contentDescription = null, tint = Violet, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(12.dp))
                                    Text("No memories yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                    Text("Chat with AI to build your profile", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                                    Spacer(Modifier.height(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(PillShape)
                                            .background(Brush.linearGradient(listOf(Violet, Pink)))
                                            .clickable(onClick = onNavigateToChat)
                                            .padding(horizontal = 20.dp, vertical = 10.dp),
                                    ) {
                                        Text("Tell AI about yourself", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                                    }
                                }
                            }
                        }
                    }

                    grouped.forEach { (category, items) ->
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(categoryColors[category] ?: TextMuted, CircleShape),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    category.replaceFirstChar { it.uppercase() },
                                    style = TextStyle(
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = categoryColors[category] ?: TextSecondary,
                                    ),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "(${items.size})",
                                    style = TextStyle(fontSize = 12.sp, color = TextMuted),
                                )
                            }
                        }
                        items(items, key = { it.id }) { memory ->
                            MemoryCard(
                                memory = memory,
                                color = categoryColors[category] ?: TextMuted,
                                onDelete = { showDeleteDialog = memory },
                            )
                        }
                        item { Spacer(Modifier.height(4.dp)) }
                    }
                }
            }
        }
    }

    // Delete confirmation
    if (showDeleteDialog != null) {
        val mem = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DialogBg,
            shape = DialogShape,
            title = {
                Text("Delete Memory", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary))
            },
            text = {
                Text(
                    "Forget \"${mem.key}\"? The AI will no longer remember this.",
                    style = TextStyle(fontSize = 14.sp, color = TextSecondary),
                )
            },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Red.copy(alpha = 0.2f))
                        .border(1.dp, Red.copy(alpha = 0.4f), PillShape)
                        .clickable {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    memorySystem.forget(mem.key)
                                    memories = memorySystem.getAll()
                                }
                            }
                            showDeleteDialog = null
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Forget", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Red))
                }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .border(0.5.dp, GlassBorder, PillShape)
                        .clickable { showDeleteDialog = null }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Cancel", style = TextStyle(fontSize = 14.sp, color = TextSecondary))
                }
            },
        )
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntity,
    color: Color,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(0.5.dp, GlassBorder, CardShape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Left color bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    memory.key,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    memory.value,
                    style = TextStyle(fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "via ${memory.source}",
                        style = TextStyle(fontSize = 10.sp, color = TextMuted),
                    )
                    if (memory.accessCount > 0) {
                        Text(
                            "accessed ${memory.accessCount}x",
                            style = TextStyle(fontSize = 10.sp, color = TextMuted),
                        )
                    }
                }
            }

            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Delete",
                tint = TextMuted,
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onDelete),
            )
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
private fun FilterPill(label: String, selected: Boolean, color: Color, count: Int = 0, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) color.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) color.copy(alpha = 0.5f) else GlassBorder,
                shape = PillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) color else TextSecondary,
                ),
            )
            if (count > 0) {
                Spacer(Modifier.width(6.dp))
                Text("$count", style = TextStyle(fontSize = 11.sp, color = if (selected) color else TextMuted))
            }
        }
    }
}
