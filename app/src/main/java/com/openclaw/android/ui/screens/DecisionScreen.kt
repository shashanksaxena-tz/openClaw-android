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
private val DialogBg = Color(0xFF12121A)
private val DialogShape = RoundedCornerShape(24.dp)

private val typeColors = mapOf(
    "decision" to Violet,
    "reflection" to Cyan,
    "lesson" to Amber,
    "journal" to Pink,
    "what_worked" to Green,
    "what_didnt" to Red,
)

private val typeIcons = mapOf(
    "decision" to Icons.Outlined.Gavel,
    "reflection" to Icons.Outlined.Psychology,
    "lesson" to Icons.Outlined.Lightbulb,
    "journal" to Icons.Outlined.EditNote,
    "what_worked" to Icons.Outlined.ThumbUp,
    "what_didnt" to Icons.Outlined.ThumbDown,
)

private val allTypes = listOf("decision", "reflection", "lesson", "journal", "what_worked", "what_didnt")

@Serializable
private data class DecisionEntry(
    val id: String,
    val type: String,
    val title: String,
    val content: String,
    val outcome: String = "",
    val lessonsLearned: String = "",
    val tags: List<String> = emptyList(),
    val createdAt: Long,
    val reviewDate: Long? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("decision_log", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var entries by remember {
        mutableStateOf(
            try {
                val raw = prefs.getString("entries_data", "[]") ?: "[]"
                json.decodeFromString<List<DecisionEntry>>(raw)
            } catch (_: Exception) { emptyList() }
        )
    }

    fun saveEntries(updated: List<DecisionEntry>) {
        entries = updated
        prefs.edit().putString("entries_data", json.encodeToString(updated)).apply()
    }

    var selectedType by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val filtered = entries
        .filter { selectedType == null || it.type == selectedType }
        .sortedByDescending { it.createdAt }

    val now = System.currentTimeMillis()
    val reviewDue = entries.count { it.reviewDate != null && it.reviewDate <= now }

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
                    .background(Brush.linearGradient(listOf(Violet, Pink)), CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Log Entry", tint = Color.White)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgBlack),
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
                            "Decisions & Growth",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                brush = Brush.linearGradient(listOf(Violet, Pink)),
                            ),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatBadge("${entries.size} entries", Violet)
                        if (reviewDue > 0) StatBadge("$reviewDue due for review", Amber)
                    }
                }
            }

            // Type filter chips
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 100)),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        TypeChip("All", selected = selectedType == null, color = Violet) { selectedType = null }
                    }
                    items(allTypes) { type ->
                        val count = entries.count { it.type == type }
                        if (count > 0) {
                            TypeChip(
                                label = type.replace("_", " ").replaceFirstChar { it.uppercase() },
                                selected = selectedType == type,
                                color = typeColors[type] ?: TextMuted,
                                count = count,
                            ) { selectedType = if (selectedType == type) null else type }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Entries list
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (filtered.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.Gavel, contentDescription = null, tint = Violet, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No entries yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                Text("Log decisions, reflections & lessons", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(Brush.linearGradient(listOf(Violet, Pink)))
                                        .clickable(onClick = onNavigateToChat)
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text("Log a decision via AI", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                                }
                            }
                        }
                    }
                }

                items(filtered, key = { it.id }) { entry ->
                    val isExpanded = entry.id in expandedIds
                    EntryCard(
                        entry = entry,
                        isExpanded = isExpanded,
                        onTap = { expandedIds = if (isExpanded) expandedIds - entry.id else expandedIds + entry.id },
                        onDelete = { showDeleteDialog = entry.id },
                    )
                }
            }
        }
    }

    // Create dialog
    if (showCreateDialog) {
        CreateEntryDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { type, title, content, tags ->
                val newEntry = DecisionEntry(
                    id = UUID.randomUUID().toString().take(8),
                    type = type,
                    title = title,
                    content = content,
                    tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    createdAt = System.currentTimeMillis(),
                )
                saveEntries(entries + newEntry)
                showCreateDialog = false
            },
        )
    }

    // Delete dialog
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DialogBg,
            shape = DialogShape,
            title = { Text("Delete Entry", style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)) },
            text = { Text("This action cannot be undone.", style = TextStyle(fontSize = 14.sp, color = TextSecondary)) },
            confirmButton = {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .background(Red.copy(alpha = 0.2f))
                        .border(1.dp, Red.copy(alpha = 0.4f), PillShape)
                        .clickable {
                            saveEntries(entries.filter { it.id != showDeleteDialog })
                            showDeleteDialog = null
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) { Text("Delete", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Red)) }
            },
            dismissButton = {
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .border(0.5.dp, GlassBorder, PillShape)
                        .clickable { showDeleteDialog = null }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) { Text("Cancel", style = TextStyle(fontSize = 14.sp, color = TextSecondary)) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    entry: DecisionEntry,
    isExpanded: Boolean,
    onTap: () -> Unit,
    onDelete: () -> Unit,
) {
    val color = typeColors[entry.type] ?: TextMuted
    val icon = typeIcons[entry.type] ?: Icons.Outlined.Article
    val df = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val now = System.currentTimeMillis()
    val isDueForReview = entry.reviewDate != null && entry.reviewDate <= now

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(
                width = if (isDueForReview) 1.dp else 0.5.dp,
                color = if (isDueForReview) Amber.copy(alpha = 0.5f) else GlassBorder,
                shape = CardShape,
            )
            .combinedClickable(onClick = onTap, onLongClick = onDelete),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.width(4.dp).heightIn(min = 70.dp).background(color))
            Column(modifier = Modifier.padding(14.dp).weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(color.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                                Text(
                                    entry.type.replace("_", " "),
                                    style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color),
                                )
                            }
                        }
                        if (isDueForReview) {
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(Amber.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("Review due", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Amber))
                            }
                        }
                    }
                    Text(df.format(Date(entry.createdAt)), style = TextStyle(fontSize = 11.sp, color = TextMuted))
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    entry.title,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    entry.content,
                    style = TextStyle(fontSize = 13.sp, color = TextSecondary, lineHeight = 18.sp),
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isExpanded) {
                    if (entry.outcome.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Flag, contentDescription = null, tint = Green, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Outcome: ${entry.outcome}", style = TextStyle(fontSize = 12.sp, color = Green))
                        }
                    }
                    if (entry.lessonsLearned.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = Amber, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lesson: ${entry.lessonsLearned}", style = TextStyle(fontSize = 12.sp, color = Amber))
                        }
                    }
                }

                if (entry.tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (tag in entry.tags.take(4)) {
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(Violet.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Text("#$tag", style = TextStyle(fontSize = 10.sp, color = Violet))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateEntryDialog(
    onDismiss: () -> Unit,
    onSave: (type: String, title: String, content: String, tags: String) -> Unit,
) {
    var selectedType by remember { mutableStateOf("decision") }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        shape = DialogShape,
        title = {
            Text(
                "Log Entry",
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, brush = Brush.linearGradient(listOf(Violet, Pink))),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Type", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allTypes.forEach { type ->
                        val color = typeColors[type] ?: TextMuted
                        val isSelected = selectedType == type
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) color.copy(alpha = 0.6f) else GlassBorder,
                                    shape = PillShape,
                                )
                                .clickable { selectedType = type }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                type.replace("_", " ").replaceFirstChar { it.uppercase() },
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) color else TextSecondary,
                                ),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = Violet,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Violet,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Details", color = TextMuted) },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = Violet,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Violet,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = Violet,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Violet,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            val canSave = title.isNotBlank() && content.isNotBlank()
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(
                        if (canSave) Brush.linearGradient(listOf(Violet, Pink))
                        else Brush.linearGradient(listOf(TextMuted.copy(alpha = 0.3f), TextMuted.copy(alpha = 0.3f)))
                    )
                    .clickable(enabled = canSave) { onSave(selectedType, title.trim(), content.trim(), tags.trim()) }
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text("Save", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
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

@Composable
private fun TypeChip(label: String, selected: Boolean, color: Color, count: Int = 0, onClick: () -> Unit) {
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

@Composable
private fun StatBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.10f))
            .border(0.5.dp, color.copy(alpha = 0.25f), PillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = color))
    }
}
