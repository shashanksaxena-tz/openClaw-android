package com.openclaw.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.data.db.ConversationEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val NeonViolet = Color(0xFFA855F7)
private val NeonCyan = Color(0xFF22D3EE)
private val NeonPink = Color(0xFFEC4899)
private val GlassBorder = Color.White.copy(alpha = 0.06f)
private val GlassSurface = Color(0xFF0D0D12).copy(alpha = 0.7f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistoryPanel(
    conversations: List<ConversationEntity>,
    activeConversationId: String?,
    onNewConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onRenameConversation: suspend (String, String) -> Unit,
    onSearchConversations: suspend (String) -> List<ConversationEntity>,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<ConversationEntity>?>(null) }
    var conversationToDelete by remember { mutableStateOf<ConversationEntity?>(null) }
    var conversationToRename by remember { mutableStateOf<ConversationEntity?>(null) }
    val scope = rememberCoroutineScope()
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    // Delete confirmation dialog — glass style
    conversationToDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            containerColor = Color(0xFF12121A),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(24.dp),
                        tint = Color(0xFFEF4444))
                }
            },
            title = {
                Text("Delete conversation?",
                    color = Color.White, fontWeight = FontWeight.SemiBold)
            },
            text = {
                Text("\"${conv.title}\" will be permanently deleted.",
                    color = Color.White.copy(alpha = 0.6f))
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteConversation(conv.id)
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            },
        )
    }

    // Rename dialog — glass style
    conversationToRename?.let { conv ->
        var newTitle by remember { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { conversationToRename = null },
            containerColor = Color(0xFF12121A),
            title = {
                Text("Rename conversation",
                    color = Color.White, fontWeight = FontWeight.SemiBold)
            },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Title", color = Color.White.copy(alpha = 0.4f)) },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonViolet,
                        unfocusedBorderColor = GlassBorder,
                        focusedContainerColor = Color(0xFF0D0D12),
                        unfocusedContainerColor = Color(0xFF0D0D12),
                        cursorColor = NeonCyan,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            onRenameConversation(conv.id, newTitle.trim())
                            conversationToRename = null
                        }
                    },
                    enabled = newTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonViolet,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { conversationToRename = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.5f))
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A10))
            .padding(top = 12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Conversations",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    brush = Brush.horizontalGradient(listOf(NeonViolet, NeonCyan)),
                ),
                modifier = Modifier.weight(1f),
            )
            // New conversation button — gradient pill
            Button(
                onClick = onNewConversation,
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(listOf(NeonViolet, NeonPink))
                    ),
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp), tint = Color.White)
                Spacer(Modifier.width(6.dp))
                Text("New", color = Color.White, fontWeight = FontWeight.Medium)
            }
        }

        // Search bar — glass style
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { query ->
                searchQuery = query
                if (query.isBlank()) {
                    searchResults = null
                } else {
                    scope.launch {
                        searchResults = onSearchConversations(query)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            placeholder = {
                Text("Search conversations...",
                    color = Color.White.copy(alpha = 0.3f))
            },
            leadingIcon = {
                Icon(Icons.Default.Search, null, Modifier.size(20.dp),
                    tint = NeonViolet.copy(alpha = 0.7f))
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                unfocusedBorderColor = GlassBorder,
                focusedBorderColor = NeonCyan.copy(alpha = 0.5f),
                unfocusedContainerColor = GlassSurface,
                focusedContainerColor = GlassSurface,
                cursorColor = NeonCyan,
            ),
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = ""; searchResults = null }) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Close, "Clear", Modifier.size(14.dp),
                                tint = Color.White.copy(alpha = 0.5f))
                        }
                    }
                }
            },
        )

        Spacer(Modifier.height(8.dp))

        val displayList = searchResults ?: conversations

        if (displayList.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val infiniteTransition = rememberInfiniteTransition(label = "empty")
                    val pulse by infiniteTransition.animateFloat(
                        initialValue = 0.9f, targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(
                            tween(2000, easing = EaseInOutSine), RepeatMode.Reverse
                        ), label = "pulse",
                    )

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .scale(pulse)
                            .clip(CircleShape)
                            .background(NeonViolet.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (searchQuery.isNotBlank()) Icons.Default.SearchOff
                            else Icons.Default.Chat,
                            null, Modifier.size(32.dp),
                            tint = NeonViolet.copy(alpha = 0.5f),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotBlank()) "No matches found"
                        else "No conversations yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.5f),
                        fontWeight = FontWeight.Medium,
                    )
                    if (searchQuery.isBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Start chatting to see history here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        } else {
            // Group by date
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

            val grouped = displayList.groupBy { conv ->
                val cal = Calendar.getInstance().apply { timeInMillis = conv.updatedAt }
                when {
                    cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "Today"
                    cal.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
                    cal.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR) -> "Yesterday"
                    else -> dateFormat.format(Date(conv.updatedAt))
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            ) {
                for ((group, convs) in grouped) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    letterSpacing = 1.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = NeonViolet.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(0.5.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                NeonViolet.copy(alpha = 0.2f),
                                                Color.Transparent,
                                            )
                                        )
                                    ),
                            )
                        }
                    }
                    items(convs, key = { it.id }) { conv ->
                        ConversationItem(
                            conversation = conv,
                            isActive = conv.id == activeConversationId,
                            timeFormat = timeFormat,
                            onClick = { onSelectConversation(conv.id) },
                            onDelete = { conversationToDelete = conv },
                            onRename = { conversationToRename = conv },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConversationItem(
    conversation: ConversationEntity,
    isActive: Boolean,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    val borderGradient = if (isActive) {
        Brush.horizontalGradient(listOf(NeonViolet, NeonCyan))
    } else {
        Brush.horizontalGradient(listOf(GlassBorder, GlassBorder))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .animateContentSize()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isActive) 1.dp else 0.5.dp,
                brush = borderGradient,
                shape = RoundedCornerShape(16.dp),
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onRename,
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) NeonViolet.copy(alpha = 0.08f)
        else GlassSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Chat icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isActive) NeonViolet.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.04f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isActive) Icons.Default.ChatBubble
                    else Icons.Default.ChatBubbleOutline,
                    null, Modifier.size(18.dp),
                    tint = if (isActive) NeonViolet
                    else Color.White.copy(alpha = 0.4f),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = conversation.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isActive) Color.White else Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${conversation.messageCount} messages · ${timeFormat.format(Date(conversation.updatedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.35f),
                )
            }
            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.04f)),
            ) {
                Icon(
                    Icons.Default.Close, "Delete",
                    Modifier.size(14.dp),
                    tint = Color.White.copy(alpha = 0.3f),
                )
            }
        }
    }
}
