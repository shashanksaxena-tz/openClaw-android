package com.openclaw.android.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openclaw.android.agent.AgentEvent
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.agent.AgentState
import com.openclaw.android.llm.ContentPart
import com.openclaw.android.ui.components.MessageBubble
import kotlinx.coroutines.launch
import android.util.Base64

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    runtime: AgentRuntime,
    onNavigateToSettings: () -> Unit,
    initialMessage: String? = null,
    initialMedia: List<Pair<String, Uri>>? = null, // mimeType to Uri pairs
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by runtime.state.collectAsState()
    val events by runtime.events.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var pendingMedia by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
    val listState = rememberLazyListState()

    // Handle initial shared content
    LaunchedEffect(initialMessage, initialMedia) {
        if (initialMessage != null || initialMedia != null) {
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
            mimeType to uri
        }
        pendingMedia = pendingMedia + newMedia
    }

    // Filter display events (skip StreamStart/StreamEnd/TokenUsage)
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
                    // Don't add the last chunk — the AssistantMessage will follow
                }
                is AgentEvent.AssistantMessage -> {
                    lastStreamChunk = null // Superseded
                    filtered.add(event)
                }
                is AgentEvent.TokenUsage -> { /* skip */ }
                else -> {
                    // If there was a pending stream chunk, add it before other events
                    if (lastStreamChunk != null && !streamComplete) {
                        filtered.add(lastStreamChunk!!)
                        lastStreamChunk = null
                    }
                    filtered.add(event)
                }
            }
        }

        // If streaming is still in progress, show the latest chunk
        if (lastStreamChunk != null && !streamComplete) {
            filtered.add(lastStreamChunk!!)
        }

        filtered
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenClaw") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    // Clear conversation
                    IconButton(onClick = { runtime.clearConversation() }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Messages list
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                if (displayEvents.isEmpty()) {
                    item {
                        EmptyState()
                    }
                }
                items(displayEvents) { event ->
                    MessageBubble(event = event)
                }

                // Loading indicator
                if (state is AgentState.Running || state is AgentState.ExecutingTool) {
                    item {
                        LoadingIndicator(state)
                    }
                }
            }

            // Pending media preview
            if (pendingMedia.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${pendingMedia.size} file(s) attached",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                // Attach button
                IconButton(
                    onClick = { filePickerLauncher.launch("*/*") },
                ) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = "Attach",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Text input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message OpenClaw...") },
                    maxLines = 5,
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                    ),
                )

                Spacer(Modifier.width(4.dp))

                // Send button
                val isRunning = state !is AgentState.Idle
                IconButton(
                    onClick = {
                        if (!isRunning && (inputText.isNotBlank() || pendingMedia.isNotEmpty())) {
                            val text = inputText
                            val media = pendingMedia.mapNotNull { (mimeType, uri) ->
                                uriToContentPart(context, mimeType, uri)
                            }
                            inputText = ""
                            pendingMedia = emptyList()
                            scope.launch {
                                runtime.sendMessage(text, media)
                            }
                        }
                    },
                    enabled = !isRunning && (inputText.isNotBlank() || pendingMedia.isNotEmpty()),
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (!isRunning && (inputText.isNotBlank() || pendingMedia.isNotEmpty()))
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "OpenClaw",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your personal AI assistant.\nShare media from any app or type a message.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingIndicator(state: AgentState) {
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Convert a content URI to a ContentPart for the LLM. */
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
