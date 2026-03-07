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
private val DialogShape = RoundedCornerShape(24.dp)
private val DialogBg = Color(0xFF12121A)

private val categoryColors = mapOf(
    "travel" to Amber,
    "meeting" to Cyan,
    "task" to Violet,
    "business_idea" to Pink,
    "email_reply" to Green,
    "personal" to TextSecondary,
    "reminder" to Amber,
    "decision" to Green,
    "team_note" to Cyan,
)

private val allCategories = listOf(
    "travel", "meeting", "task", "business_idea", "email_reply",
    "personal", "reminder", "decision", "team_note",
)

@Composable
fun NotesScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("smart_notes", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var notes by remember {
        mutableStateOf(
            try {
                val raw = prefs.getString("notes_data", "[]") ?: "[]"
                json.parseToJsonElement(raw).jsonArray.toList()
            } catch (_: Exception) { emptyList() }
        )
    }

    // Helper to persist notes
    fun saveNotes(updated: List<JsonElement>) {
        notes = updated
        prefs.edit().putString("notes_data", json.encodeToString(JsonArray(updated))).apply()
    }

    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Dialog states
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) } // holds note ID to delete

    // Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    // Expanded notes tracking
    var expandedNoteIds by remember { mutableStateOf(setOf<String>()) }

    val categories = notes.mapNotNull { it.jsonObject["category"]?.jsonPrimitive?.contentOrNull }.distinct()

    val filteredNotes = notes.filter { n ->
        val category = n.jsonObject["category"]?.jsonPrimitive?.contentOrNull ?: ""
        val content = n.jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
        val matchesCategory = selectedCategory == null || category == selectedCategory
        val matchesSearch = searchQuery.isBlank() ||
                content.contains(searchQuery, ignoreCase = true) ||
                category.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }.sortedByDescending { it.jsonObject["createdAt"]?.jsonPrimitive?.longOrNull ?: 0 }

    // Handle delete confirmation snackbar
    LaunchedEffect(showDeleteDialog) {
        // no-op; we use a dialog instead
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = BgBlack,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SurfaceLight,
                    contentColor = TextPrimary,
                    actionColor = Cyan,
                    shape = CardShape,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = CircleShape,
                containerColor = Color.Transparent,
                modifier = Modifier
                    .size(56.dp)
                    .background(Brush.linearGradient(listOf(Violet, Cyan)), CircleShape),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Note", tint = Color.White)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(BgBlack)
        ) {
            // -- Header --
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 2 },
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Notes",
                            style = TextStyle(
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                brush = Brush.linearGradient(listOf(Cyan, Violet)),
                            ),
                        )
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(GlassBg)
                                .border(0.5.dp, GlassBorder, PillShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("${notes.size} total", style = TextStyle(fontSize = 12.sp, color = TextMuted))
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // Search bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardShape)
                            .background(SurfaceLight)
                            .border(0.5.dp, GlassBorder, CardShape)
                            .padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(Violet),
                                decorationBox = { inner ->
                                    if (searchQuery.isEmpty()) Text("Search notes...", style = TextStyle(fontSize = 14.sp, color = TextMuted))
                                    inner()
                                },
                            )
                            if (searchQuery.isNotEmpty()) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = TextMuted,
                                    modifier = Modifier.size(18.dp).clickable { searchQuery = "" },
                                )
                            }
                        }
                    }
                }
            }

            // -- Category Chips --
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 100)),
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        CategoryChip(label = "All", selected = selectedCategory == null, color = Violet, onClick = { selectedCategory = null })
                    }
                    items(categories) { cat ->
                        CategoryChip(
                            label = cat.replace("_", " ").replaceFirstChar { it.uppercase() },
                            selected = selectedCategory == cat,
                            color = categoryColors[cat] ?: TextMuted,
                            count = notes.count { it.jsonObject["category"]?.jsonPrimitive?.contentOrNull == cat },
                            onClick = { selectedCategory = if (selectedCategory == cat) null else cat },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // -- Notes List --
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (filteredNotes.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.EditNote, contentDescription = null, tint = Cyan, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No notes yet", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                Text("Dictate or type to capture thoughts", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(Brush.linearGradient(listOf(Cyan, Violet)))
                                        .clickable(onClick = onNavigateToChat)
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text("Capture a note via AI", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                                }
                            }
                        }
                    }
                }

                items(filteredNotes, key = { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString() }) { note ->
                    val noteId = note.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val isExpanded = noteId in expandedNoteIds
                    NoteCard(
                        note = note,
                        isExpanded = isExpanded,
                        onTap = {
                            expandedNoteIds = if (isExpanded) expandedNoteIds - noteId else expandedNoteIds + noteId
                        },
                        onLongPress = {
                            showDeleteDialog = noteId
                        },
                    )
                }
            }
        }
    }

    // -- Create Note Dialog --
    if (showCreateDialog) {
        CreateNoteDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { content, category, priority, tags ->
                val tagsArray = tags
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { JsonPrimitive(it) }

                val newNote = buildJsonObject {
                    put("id", JsonPrimitive(UUID.randomUUID().toString()))
                    put("content", JsonPrimitive(content))
                    put("category", JsonPrimitive(category))
                    put("priority", JsonPrimitive(priority))
                    put("tags", JsonArray(tagsArray))
                    put("createdAt", JsonPrimitive(System.currentTimeMillis()))
                }
                saveNotes(notes + newNote)
                showCreateDialog = false
            },
        )
    }

    // -- Delete Confirmation Dialog --
    if (showDeleteDialog != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            containerColor = DialogBg,
            shape = DialogShape,
            title = {
                Text(
                    "Delete Note",
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary),
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this note? This action cannot be undone.",
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
                            val idToDelete = showDeleteDialog
                            val updated = notes.filter { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull != idToDelete }
                            saveNotes(updated)
                            showDeleteDialog = null
                        }
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    Text("Delete", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Red))
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

private fun suggestCategory(text: String): String? {
    val lower = text.lowercase()
    return when {
        lower.containsAny("flight", "hotel", "trip", "travel", "vacation", "airport", "booking", "itinerary") -> "travel"
        lower.containsAny("meeting", "agenda", "standup", "sync", "call with", "discussion") -> "meeting"
        lower.containsAny("todo", "task", "finish", "complete", "deadline", "due", "deliver") -> "task"
        lower.containsAny("idea", "startup", "business", "revenue", "product", "market", "launch") -> "business_idea"
        lower.containsAny("email", "reply", "respond", "draft", "send to", "cc", "subject") -> "email_reply"
        lower.containsAny("remind", "remember to", "don't forget", "alert", "notify") -> "reminder"
        lower.containsAny("decide", "decision", "choose", "option", "pros and cons", "tradeoff") -> "decision"
        lower.containsAny("team", "delegate", "assign", "sprint", "project") -> "team_note"
        else -> null
    }
}

private fun String.containsAny(vararg keywords: String): Boolean = keywords.any { this.contains(it) }

private fun suggestTags(text: String): List<String> {
    val lower = text.lowercase()
    val tags = mutableListOf<String>()
    if (lower.containsAny("urgent", "asap", "important", "critical")) tags.add("urgent")
    if (lower.containsAny("follow up", "followup", "follow-up")) tags.add("follow-up")
    if (lower.containsAny("money", "cost", "price", "budget", "payment", "$")) tags.add("finance")
    if (lower.containsAny("tomorrow", "today", "tonight", "this week")) tags.add("time-sensitive")
    return tags.take(3)
}

@Composable
private fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onSave: (content: String, category: String, priority: String, tags: String) -> Unit,
) {
    var content by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("personal") }
    var priority by remember { mutableStateOf("normal") }
    var tags by remember { mutableStateOf("") }
    var hasAutoSuggested by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        shape = DialogShape,
        title = {
            Text(
                "Create Note",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(listOf(Cyan, Violet)),
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { newContent ->
                        content = newContent
                        // Auto-suggest category and tags when user types enough
                        if (newContent.length > 15 && !hasAutoSuggested) {
                            suggestCategory(newContent)?.let { selectedCategory = it }
                            val suggested = suggestTags(newContent)
                            if (suggested.isNotEmpty() && tags.isBlank()) {
                                tags = suggested.joinToString(", ")
                            }
                            if (newContent.lowercase().let { it.contains("urgent") || it.contains("asap") || it.contains("critical") }) {
                                priority = "high"
                            }
                            hasAutoSuggested = true
                        }
                        if (newContent.length <= 15) hasAutoSuggested = false
                    },
                    label = { Text("Content", color = TextMuted) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = Cyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Cyan,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )

                // Auto-categorize hint
                if (hasAutoSuggested && suggestCategory(content) != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = Cyan, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Auto-categorized as ${selectedCategory.replace("_", " ")}",
                            style = TextStyle(fontSize = 11.sp, color = Cyan),
                        )
                    }
                }

                // Category selector
                Text("Category", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary))
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    allCategories.forEach { cat ->
                        val color = categoryColors[cat] ?: TextMuted
                        val isSelected = selectedCategory == cat
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(if (isSelected) color.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = if (isSelected) 1.dp else 0.5.dp,
                                    color = if (isSelected) color.copy(alpha = 0.6f) else GlassBorder,
                                    shape = PillShape,
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                cat.replace("_", " ").replaceFirstChar { it.uppercase() },
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) color else TextSecondary,
                                ),
                            )
                        }
                    }
                }

                // Priority toggle
                Text("Priority", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("normal" to TextSecondary, "high" to Red).forEach { (level, color) ->
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(if (priority == level) color.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    width = if (priority == level) 1.dp else 0.5.dp,
                                    color = if (priority == level) color.copy(alpha = 0.6f) else GlassBorder,
                                    shape = PillShape,
                                )
                                .clickable { priority = level }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                level.replaceFirstChar { it.uppercase() },
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = if (priority == level) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (priority == level) color else TextSecondary,
                                ),
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma-separated)", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Cyan,
                        unfocusedBorderColor = GlassBorder,
                        cursorColor = Cyan,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedLabelColor = Cyan,
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(
                        if (content.isNotBlank()) Brush.linearGradient(listOf(Cyan, Violet))
                        else Brush.linearGradient(listOf(TextMuted.copy(alpha = 0.3f), TextMuted.copy(alpha = 0.3f)))
                    )
                    .clickable(enabled = content.isNotBlank()) { onSave(content.trim(), selectedCategory, priority, tags.trim()) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: JsonElement,
    isExpanded: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val content = note.jsonObject["content"]?.jsonPrimitive?.contentOrNull ?: ""
    val category = note.jsonObject["category"]?.jsonPrimitive?.contentOrNull ?: "personal"
    val priority = note.jsonObject["priority"]?.jsonPrimitive?.contentOrNull ?: "normal"
    val createdAt = note.jsonObject["createdAt"]?.jsonPrimitive?.longOrNull ?: 0
    val tags = try {
        note.jsonObject["tags"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
    } catch (_: Exception) { emptyList() }
    val df = remember { SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()) }
    val color = categoryColors[category] ?: TextMuted

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(0.5.dp, GlassBorder, CardShape)
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress,
            ),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left color bar
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .heightIn(min = 60.dp)
                    .background(color),
            )

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
                            Text(
                                category.replace("_", " "),
                                style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color),
                            )
                        }
                        if (priority == "high") {
                            Box(
                                modifier = Modifier
                                    .clip(PillShape)
                                    .background(Red.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text("!!!", style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Red))
                            }
                        }
                    }
                    Text(df.format(Date(createdAt)), style = TextStyle(fontSize = 11.sp, color = TextMuted))
                }

                Spacer(Modifier.height(8.dp))

                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                    },
                    label = "note_expand",
                ) { expanded ->
                    if (expanded) {
                        Text(
                            content,
                            style = TextStyle(fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp),
                        )
                    } else {
                        Text(
                            content,
                            style = TextStyle(fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (tag in tags.take(4)) {
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

                // Expansion indicator
                if (content.length > 200) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isExpanded) "Tap to collapse" else "Tap to expand",
                        style = TextStyle(fontSize = 10.sp, color = TextMuted),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, color: Color, count: Int = 0, onClick: () -> Unit) {
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
