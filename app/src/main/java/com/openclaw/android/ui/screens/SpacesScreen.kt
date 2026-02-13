package com.openclaw.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.data.Space
import com.openclaw.android.data.SpaceManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpacesScreen(
    spaceManager: SpaceManager,
    agentRuntime: AgentRuntime,
    modifier: Modifier = Modifier,
) {
    var spaces by remember { mutableStateOf(spaceManager.getSpaces()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var spaceToDelete by remember { mutableStateOf<Space?>(null) }
    val activeSpaceName = agentRuntime.activeSpaceName

    // Create dialog
    if (showCreateDialog) {
        CreateSpaceDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, emoji, description ->
                spaceManager.createSpace(name, emoji, description)
                spaces = spaceManager.getSpaces()
                showCreateDialog = false
            },
        )
    }

    // Delete confirmation
    spaceToDelete?.let { space ->
        AlertDialog(
            onDismissRequest = { spaceToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete \"${space.name}\"?") },
            text = { Text("This will delete the space, all its files, conversations, and knowledge base. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        spaceManager.deleteSpace(space.id)
                        if (agentRuntime.activeSpaceName?.contains(space.name) == true) {
                            agentRuntime.setActiveSpace(null)
                        }
                        spaces = spaceManager.getSpaces()
                        spaceToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { spaceToDelete = null }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Spaces",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "Organize your projects with dedicated files and context",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Space")
            }
        }

        // Active space indicator
        if (activeSpaceName != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Active: $activeSpaceName",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { agentRuntime.setActiveSpace(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) {
                        Text("Deactivate", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (spaces.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Workspaces,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No spaces yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Create a space to organize your projects.\nEach space has its own files and context.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(spaces, key = { it.id }) { space ->
                    SpaceCard(
                        space = space,
                        isActive = agentRuntime.activeSpaceName?.contains(space.name) == true,
                        onActivate = {
                            agentRuntime.setActiveSpace(space.id)
                            agentRuntime.clearConversation()
                        },
                        onDelete = { spaceToDelete = space },
                    )
                }
            }
        }
    }
}

@Composable
private fun SpaceCard(
    space: Space,
    isActive: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onActivate),
        border = CardDefaults.outlinedCardBorder().copy(
            width = if (isActive) 2.dp else 1.dp,
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Emoji avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = space.emoji,
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = space.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (space.description.isNotBlank()) {
                    Text(
                        text = space.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "Created ${dateFormat.format(Date(space.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }

            // Actions
            if (isActive) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Active",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("📁") }
    var description by remember { mutableStateOf("") }

    val emojiOptions = listOf("📁", "💼", "📝", "🎨", "🔬", "📊", "🎵", "📸", "🏠", "✈️", "🍳", "💪", "📖", "🎯", "💡")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Space") },
        text = {
            Column {
                // Emoji picker
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (e in emojiOptions.take(8)) {
                        FilterChip(
                            selected = emoji == e,
                            onClick = { emoji = e },
                            label = { Text(e) },
                            modifier = Modifier.height(32.dp),
                        )
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    for (e in emojiOptions.drop(8)) {
                        FilterChip(
                            selected = emoji == e,
                            onClick = { emoji = e },
                            label = { Text(e) },
                            modifier = Modifier.height(32.dp),
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Space Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), emoji, description.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
