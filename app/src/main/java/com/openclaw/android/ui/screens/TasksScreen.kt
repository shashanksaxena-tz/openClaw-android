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
private val Surface = Color(0xFF0D0D12)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    modifier: Modifier = Modifier,
    onNavigateToChat: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("task_manager", Context.MODE_PRIVATE) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var tasks by remember {
        mutableStateOf(
            try {
                val raw = prefs.getString("tasks_data", "[]") ?: "[]"
                json.parseToJsonElement(raw).jsonArray.toList()
            } catch (_: Exception) { emptyList() }
        )
    }

    // Helper to persist tasks
    fun saveTasks(updated: List<JsonElement>) {
        tasks = updated
        prefs.edit().putString("tasks_data", json.encodeToString(JsonArray(updated))).apply()
    }

    var selectedFilter by remember { mutableStateOf("all") }
    var selectedProject by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // Dialog state
    var showCreateDialog by remember { mutableStateOf(false) }

    // Snackbar state for undo delete
    val snackbarHostState = remember { SnackbarHostState() }
    var recentlyDeletedTask by remember { mutableStateOf<JsonElement?>(null) }
    var recentlyDeletedIndex by remember { mutableStateOf(-1) }

    val filteredTasks = tasks.filter { t ->
        val status = t.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
        val project = t.jsonObject["project"]?.jsonPrimitive?.contentOrNull ?: "general"
        val matchesFilter = when (selectedFilter) {
            "pending" -> status == "pending" || status == "in_progress"
            "done" -> status == "done"
            "high" -> (t.jsonObject["priority"]?.jsonPrimitive?.contentOrNull == "high") && status != "done"
            "overdue" -> status != "done" && (t.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE) < System.currentTimeMillis()
            else -> true
        }
        val matchesProject = selectedProject == null || project.equals(selectedProject, ignoreCase = true)
        matchesFilter && matchesProject
    }.sortedWith(compareBy<JsonElement> {
        when (it.jsonObject["priority"]?.jsonPrimitive?.contentOrNull) { "high" -> 0; "medium" -> 1; else -> 2 }
    }.thenBy { it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE })

    val allProjects = tasks.mapNotNull { it.jsonObject["project"]?.jsonPrimitive?.contentOrNull }.distinct()
    val pendingCount = tasks.count { (it.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: "pending") != "done" }
    val doneCount = tasks.count { it.jsonObject["status"]?.jsonPrimitive?.contentOrNull == "done" }
    val overdueCount = tasks.count {
        (it.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: "pending") != "done" &&
        (it.jsonObject["deadline"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE) < System.currentTimeMillis()
    }

    // Undo delete handler
    LaunchedEffect(recentlyDeletedTask) {
        if (recentlyDeletedTask != null) {
            val result = snackbarHostState.showSnackbar(
                message = "Task deleted",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed && recentlyDeletedTask != null) {
                val restored = tasks.toMutableList()
                val insertAt = recentlyDeletedIndex.coerceIn(0, restored.size)
                restored.add(insertAt, recentlyDeletedTask!!)
                saveTasks(restored)
            }
            recentlyDeletedTask = null
        }
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
                    actionColor = Violet,
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
                Icon(Icons.Default.Add, contentDescription = "Create Task", tint = Color.White)
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
                    Text(
                        "Tasks",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            brush = Brush.linearGradient(listOf(Violet, Cyan)),
                        ),
                    )
                    Spacer(Modifier.height(4.dp))

                    // Stats chips
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CountChip("$pendingCount pending", Violet)
                        CountChip("$doneCount done", Green)
                        if (overdueCount > 0) CountChip("$overdueCount overdue", Red)
                    }
                }
            }

            // -- Filter Chips --
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(400, 100)) + slideInVertically(tween(400, 100)) { it / 3 },
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val filters = listOf("all" to "All", "pending" to "Pending", "high" to "High Priority", "overdue" to "Overdue", "done" to "Done")
                    items(filters) { (key, label) ->
                        FilterChip(label = label, selected = selectedFilter == key, onClick = { selectedFilter = key })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // -- Project Filter --
            if (allProjects.size > 1) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(400, 150)),
                ) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            FilterChip(label = "All Projects", selected = selectedProject == null, onClick = { selectedProject = null })
                        }
                        items(allProjects) { project ->
                            FilterChip(label = project, selected = selectedProject == project, onClick = { selectedProject = project })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // -- Task List --
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filteredTasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("All caught up!", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary))
                                Text("No tasks match this filter", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                                Spacer(Modifier.height(16.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(Brush.linearGradient(listOf(Violet, Cyan)))
                                        .clickable(onClick = onNavigateToChat)
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text("Ask AI to create a task", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White))
                                }
                            }
                        }
                    }
                }

                items(filteredTasks, key = { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: UUID.randomUUID().toString() }) { task ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                val taskId = task.jsonObject["id"]?.jsonPrimitive?.contentOrNull
                                if (taskId != null) {
                                    val idx = tasks.indexOfFirst { it.jsonObject["id"]?.jsonPrimitive?.contentOrNull == taskId }
                                    if (idx >= 0) {
                                        recentlyDeletedTask = tasks[idx]
                                        recentlyDeletedIndex = idx
                                        saveTasks(tasks.filterIndexed { i, _ -> i != idx })
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val alignment = Alignment.CenterEnd
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CardShape)
                                    .background(Red.copy(alpha = 0.2f))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = alignment,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Red, modifier = Modifier.size(24.dp))
                            }
                        },
                    ) {
                        TaskCard(
                            task = task,
                            onToggleStatus = {
                                val taskId = task.jsonObject["id"]?.jsonPrimitive?.contentOrNull ?: return@TaskCard
                                val updated = tasks.map { t ->
                                    if (t.jsonObject["id"]?.jsonPrimitive?.contentOrNull == taskId) {
                                        val currentStatus = t.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
                                        val newStatus = if (currentStatus == "done") "pending" else "done"
                                        buildJsonObject {
                                            t.jsonObject.forEach { (k, v) ->
                                                if (k == "status") put(k, JsonPrimitive(newStatus))
                                                else put(k, v)
                                            }
                                        }
                                    } else t
                                }
                                saveTasks(updated)
                            },
                        )
                    }
                }
            }
        }
    }

    // -- Create Task Dialog --
    if (showCreateDialog) {
        CreateTaskDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, description, priority, project ->
                val newTask = buildJsonObject {
                    put("id", JsonPrimitive(UUID.randomUUID().toString()))
                    put("title", JsonPrimitive(title))
                    put("description", JsonPrimitive(description))
                    put("priority", JsonPrimitive(priority))
                    put("status", JsonPrimitive("pending"))
                    put("project", JsonPrimitive(project.ifBlank { "general" }))
                    put("createdAt", JsonPrimitive(System.currentTimeMillis()))
                }
                saveTasks(tasks + newTask)
                showCreateDialog = false
            },
        )
    }
}

@Composable
private fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, description: String, priority: String, project: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    var project by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DialogBg,
        shape = DialogShape,
        title = {
            Text(
                "Create Task",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(listOf(Violet, Cyan)),
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = TextMuted) },
                    minLines = 3,
                    maxLines = 5,
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

                // Priority selector
                Text("Priority", style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextSecondary))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("high" to Red, "medium" to Amber, "low" to TextMuted).forEach { (level, color) ->
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
                    value = project,
                    onValueChange = { project = it },
                    label = { Text("Project (optional)", color = TextMuted) },
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
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .background(
                        if (title.isNotBlank()) Brush.linearGradient(listOf(Violet, Cyan))
                        else Brush.linearGradient(listOf(TextMuted.copy(alpha = 0.3f), TextMuted.copy(alpha = 0.3f)))
                    )
                    .clickable(enabled = title.isNotBlank()) { onCreate(title.trim(), description.trim(), priority, project.trim()) }
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

@Composable
private fun TaskCard(task: JsonElement, onToggleStatus: () -> Unit) {
    val title = task.jsonObject["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
    val description = task.jsonObject["description"]?.jsonPrimitive?.contentOrNull ?: ""
    val priority = task.jsonObject["priority"]?.jsonPrimitive?.contentOrNull ?: "medium"
    val status = task.jsonObject["status"]?.jsonPrimitive?.contentOrNull ?: "pending"
    val project = task.jsonObject["project"]?.jsonPrimitive?.contentOrNull ?: ""
    val assignedTo = task.jsonObject["assigned_to"]?.jsonPrimitive?.contentOrNull
        ?: task.jsonObject["assignedTo"]?.jsonPrimitive?.contentOrNull ?: ""
    val deadline = task.jsonObject["deadline"]?.jsonPrimitive?.longOrNull
    val df = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }

    val priorityColor = when (priority) { "high" -> Red; "medium" -> Amber; else -> TextMuted }
    val statusColor = when (status) { "done" -> Green; "in_progress" -> Cyan; "blocked" -> Red; else -> TextMuted }
    val isDone = status == "done"
    val isOverdue = !isDone && deadline != null && deadline < System.currentTimeMillis()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(SurfaceLight)
            .border(
                width = 0.5.dp,
                color = if (isOverdue) Red.copy(alpha = 0.3f) else GlassBorder,
                shape = CardShape,
            )
            .clickable(onClick = onToggleStatus)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Tappable completion circle
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(22.dp)
                    .then(
                        if (isDone) Modifier.background(Green, CircleShape)
                        else Modifier.border(2.dp, priorityColor, CircleShape)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isDone) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Done",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDone) TextMuted else TextPrimary,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = TextStyle(fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Metadata row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (project.isNotBlank() && project != "general") {
                        MetadataPill(text = project, color = Violet)
                    }
                    MetadataPill(text = status.replace("_", " "), color = statusColor)
                    if (assignedTo.isNotBlank()) {
                        MetadataPill(text = assignedTo, color = Cyan)
                    }
                }
            }

            // Deadline
            if (deadline != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        df.format(Date(deadline)),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isOverdue) Red else TextMuted,
                        ),
                    )
                    if (isOverdue) {
                        Text("OVERDUE", style = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Red))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(if (selected) Violet.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) Violet.copy(alpha = 0.5f) else GlassBorder,
                shape = PillShape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Violet else TextSecondary,
            ),
        )
    }
}

@Composable
private fun CountChip(text: String, color: Color) {
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
private fun MetadataPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(PillShape)
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(text, style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = color))
    }
}
