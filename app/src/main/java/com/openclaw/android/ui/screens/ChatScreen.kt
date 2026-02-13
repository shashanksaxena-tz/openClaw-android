package com.openclaw.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.openclaw.android.agent.AgentEvent
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.agent.AgentState
import com.openclaw.android.llm.ContentPart
import com.openclaw.android.ui.components.*
import kotlinx.coroutines.launch
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    runtime: AgentRuntime,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialMessage: String? = null,
    initialMedia: List<Pair<String, Uri>>? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by runtime.state.collectAsState()
    val events by runtime.events.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var pendingMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val listState = rememberLazyListState()

    // Handle initial shared content (only once)
    var initialHandled by remember { mutableStateOf(false) }
    LaunchedEffect(initialMessage, initialMedia) {
        if (!initialHandled && (initialMessage != null || initialMedia != null)) {
            initialHandled = true
            val text = initialMessage ?: "I shared some content with you."
            val media = initialMedia?.mapNotNull { (mimeType, uri) ->
                uriToContentPart(context, mimeType, uri)
            } ?: emptyList()
            runtime.sendMessage(text, media)
        }
    }

    // Auto-scroll to bottom
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            listState.animateScrollToItem(events.size - 1)
        }
    }

    // File picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        val newMedia = uris.map { uri ->
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            mimeTypeToMediaItem(mimeType, uri)
        }
        pendingMedia = pendingMedia + newMedia
    }

    // Filter display events
    val displayEvents = remember(events) {
        val filtered = mutableListOf<AgentEvent>()
        var lastStreamChunk: AgentEvent.StreamChunk? = null
        var streamComplete = false

        for (event in events) {
            when (event) {
                is AgentEvent.StreamStart -> {
                    lastStreamChunk = null
                    streamComplete = false
                }
                is AgentEvent.StreamChunk -> {
                    lastStreamChunk = event
                }
                is AgentEvent.StreamEnd -> {
                    streamComplete = true
                }
                is AgentEvent.AssistantMessage -> {
                    lastStreamChunk = null
                    filtered.add(event)
                }
                is AgentEvent.TokenUsage -> { /* skip */ }
                else -> {
                    if (lastStreamChunk != null && !streamComplete) {
                        filtered.add(lastStreamChunk!!)
                        lastStreamChunk = null
                    }
                    filtered.add(event)
                }
            }
        }

        if (lastStreamChunk != null && !streamComplete) {
            filtered.add(lastStreamChunk!!)
        }
        filtered
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar
        Surface(tonalElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "OpenClaw",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp),
                )

                runtime.activeSpaceName?.let { spaceName ->
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(spaceName, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(28.dp),
                    )
                }

                Spacer(Modifier.weight(1f))

                IconButton(onClick = { runtime.clearConversation() }) {
                    Icon(Icons.Default.ClearAll, contentDescription = "Clear chat")
                }
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            if (displayEvents.isEmpty()) {
                item { EmptyState() }
            }
            items(displayEvents) { event ->
                MessageBubble(event = event)
            }

            if (state is AgentState.Running || state is AgentState.ExecutingTool) {
                item { LoadingIndicator(state) }
            }
        }

        // Pending media preview strip
        AnimatedVisibility(
            visible = pendingMedia.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            MediaPreviewStrip(
                media = pendingMedia,
                onRemove = { index ->
                    pendingMedia = pendingMedia.toMutableList().also { it.removeAt(index) }
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        // Input bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message OpenClaw...") },
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                )

                Spacer(Modifier.width(4.dp))

                VoiceInputButton(
                    onResult = { spokenText ->
                        if (spokenText.isNotBlank()) {
                            scope.launch {
                                runtime.sendMessage(spokenText, emptyList())
                            }
                        }
                    },
                    enabled = state is AgentState.Idle,
                )

                val canSend = state is AgentState.Idle && (inputText.isNotBlank() || pendingMedia.isNotEmpty())
                IconButton(
                    onClick = {
                        if (canSend) {
                            val text = inputText.ifBlank { "Here are the files I'm sharing." }
                            val media = pendingMedia.mapNotNull { item ->
                                mediaItemToContentPart(context, item)
                            }
                            inputText = ""
                            pendingMedia = emptyList()
                            scope.launch {
                                runtime.sendMessage(text, media)
                            }
                        }
                    },
                    enabled = canSend,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (canSend) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = EaseInOutCubic),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "scale",
        )

        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(56.dp)
                .scale(scale),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "OpenClaw",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your personal AI assistant.\nType a message, use voice, or share media from any app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LoadingIndicator(state: AgentState) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (state) {
                is AgentState.ExecutingTool -> "Running ${state.toolName}..."
                else -> "Thinking..."
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
        )
    }
}

private fun uriToContentPart(
    context: android.content.Context,
    mimeType: String,
    uri: Uri,
): ContentPart? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.readBytes()
        inputStream.close()

        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

        when {
            mimeType.startsWith("image/") -> ContentPart(
                type = "image_base64",
                mediaType = mimeType,
                data = base64,
            )
            mimeType.startsWith("audio/") -> ContentPart(
                type = "audio_base64",
                mediaType = mimeType,
                data = base64,
            )
            mimeType.startsWith("text/") -> {
                val text = String(bytes)
                ContentPart(type = "text", text = "Shared file content:\n$text")
            }
            else -> ContentPart(
                type = "text",
                text = "Shared file: ${uri.lastPathSegment} (type: $mimeType, ${bytes.size} bytes)",
            )
        }
    } catch (e: Exception) {
        ContentPart(type = "text", text = "Failed to read shared file: ${e.message}")
    }
}

private fun mediaItemToContentPart(
    context: android.content.Context,
    item: MediaItem,
): ContentPart? {
    val uri = when (item) {
        is MediaItem.Image -> item.uri
        is MediaItem.Video -> item.uri
        is MediaItem.Audio -> item.uri
        is MediaItem.File -> item.uri
    }
    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
    return uriToContentPart(context, mimeType, uri)
}

private fun mimeTypeToMediaItem(mimeType: String, uri: Uri): MediaItem {
    return when {
        mimeType.startsWith("image/") -> MediaItem.Image(uri, uri.lastPathSegment ?: "image")
        mimeType.startsWith("video/") -> MediaItem.Video(uri, uri.lastPathSegment ?: "video")
        mimeType.startsWith("audio/") -> MediaItem.Audio(uri, uri.lastPathSegment ?: "audio")
        else -> MediaItem.File(uri, uri.lastPathSegment ?: "file", mimeType)
    }
}
