package com.openclaw.android.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.util.Base64
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    runtime: AgentRuntime,
    onNavigateToSettings: () -> Unit,
    onOpenDrawer: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    modelRouter: com.openclaw.android.llm.ModelRouter? = null,
    initialMessage: String? = null,
    initialMedia: List<Pair<String, Uri>>? = null,
    onExportChat: (suspend (filename: String) -> Boolean)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by runtime.state.collectAsState()
    val events by runtime.events.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var pendingMedia by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showModelPicker by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var justSent by remember { mutableStateOf(false) }

    // Network connectivity
    var isOnline by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) { isOnline = false }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm?.registerNetworkCallback(request, callback)
        // Initial check
        val activeNet = cm?.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        onDispose { cm?.unregisterNetworkCallback(callback) }
    }

    // Handle initial shared content (only once)
    var initialHandled by remember { mutableStateOf(false) }
    LaunchedEffect(initialMessage, initialMedia) {
        if (!initialHandled && (initialMessage != null || initialMedia != null)) {
            initialHandled = true
            val text = initialMessage ?: "I shared some content with you."
            val media = initialMedia?.mapNotNull { (mimeType, uri) ->
                withContext(Dispatchers.IO) { uriToContentPart(context, mimeType, uri) }
            } ?: emptyList()
            runtime.sendMessage(text, media)
        }
    }

    // Auto-scroll to bottom
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            justSent = false
            listState.animateScrollToItem(events.size - 1)
        }
    }

    // Reset justSent when state becomes Running
    LaunchedEffect(state) {
        if (state is AgentState.Running) {
            justSent = false
        }
    }

    // File picker - only safe types
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
                is AgentEvent.StreamStart -> { lastStreamChunk = null; streamComplete = false }
                is AgentEvent.StreamChunk -> lastStreamChunk = event
                is AgentEvent.StreamEnd -> streamComplete = true
                is AgentEvent.AssistantMessage -> { lastStreamChunk = null; filtered.add(event) }
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
        if (lastStreamChunk != null && !streamComplete) filtered.add(lastStreamChunk!!)
        filtered
    }

    val isRunning = state is AgentState.Running || state is AgentState.ExecutingTool || justSent

    // Show snackbar on network errors
    LaunchedEffect(events) {
        val lastEvent = events.lastOrNull()
        if (lastEvent is AgentEvent.Error) {
            snackbarHostState.showSnackbar(
                message = lastEvent.message ?: "A network error occurred. Please try again.",
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Export dialog
    if (showExportDialog && onExportChat != null) {
        var exportFilename by remember { mutableStateOf("conversation-${System.currentTimeMillis() / 1000}.md") }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = { Icon(Icons.Default.FileDownload, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Export Conversation") },
            text = {
                Column {
                    Text("Save this conversation to your workspace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = exportFilename,
                        onValueChange = { exportFilename = it },
                        label = { Text("Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val success = onExportChat(exportFilename)
                            if (success) {
                                snackbarHostState.showSnackbar("Exported to workspace/$exportFilename")
                            } else {
                                snackbarHostState.showSnackbar("Export failed")
                            }
                            showExportDialog = false
                        }
                    },
                    enabled = exportFilename.isNotBlank(),
                ) { Text("Export") }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { scaffoldPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding)) {
            // Top bar
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onOpenDrawer != null) {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Default.Menu, contentDescription = "Open drawer")
                        }
                    }

                    Text("OpenClaw", style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = if (onOpenDrawer != null) 0.dp else 8.dp))

                    runtime.activeSpaceName?.let { spaceName ->
                        Spacer(Modifier.width(8.dp))
                        AssistChip(onClick = {}, label = { Text(spaceName, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp))
                    }

                    // Model indicator chip - tap to switch
                    Spacer(Modifier.width(8.dp))
                    val currentModelName = runtime.activeModelName ?: "Auto"
                    AssistChip(
                        onClick = { if (modelRouter != null) showModelPicker = true else onNavigateToSettings() },
                        label = { Text(currentModelName, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(Icons.Default.Memory, contentDescription = null,
                                modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.height(28.dp),
                    )

                    Spacer(Modifier.weight(1f))

                    // Export chat button
                    if (onExportChat != null && events.isNotEmpty()) {
                        IconButton(onClick = { showExportDialog = true }) {
                            Icon(Icons.Default.FileDownload, contentDescription = "Export chat")
                        }
                    }

                    // New conversation button
                    IconButton(onClick = { scope.launch { runtime.startNewConversation() } }) {
                        Icon(Icons.Default.Add, contentDescription = "New conversation")
                    }

                    // Clear button
                    IconButton(onClick = { scope.launch { runtime.clearConversation() } }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear chat")
                    }
                }
            }

            // Offline banner
            AnimatedVisibility(
                visible = !isOnline,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.WifiOff, null, Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text("You're offline. Check your connection.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error)
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
                    item {
                        EmptyState(
                            onSuggestionClick = { prompt ->
                                inputText = prompt
                            },
                        )
                    }
                }
                items(displayEvents) { event ->
                    MessageBubble(
                        event = event,
                        onRetry = if (event is AgentEvent.Error) {
                            { scope.launch { runtime.retryLastMessage() } }
                        } else null,
                    )
                }

                if (isRunning) {
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
                    onRemove = { index -> pendingMedia = pendingMedia.toMutableList().also { it.removeAt(index) } },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // Input bar
            Surface(tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    // Attach (only safe types)
                    IconButton(onClick = { filePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.AttachFile, "Attach",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message OpenClaw...") },
                        maxLines = 15,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        ),
                    )

                    Spacer(Modifier.width(4.dp))

                    if (isRunning) {
                        // Stop button
                        IconButton(onClick = { runtime.cancel() }) {
                            Icon(Icons.Default.Stop, "Stop",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        // Voice input - includes pending media
                        VoiceInputButton(
                            onResult = { spokenText ->
                                if (spokenText.isNotBlank()) {
                                    scope.launch {
                                        val media = pendingMedia.mapNotNull { item ->
                                            withContext(Dispatchers.IO) { mediaItemToContentPart(context, item) }
                                        }
                                        pendingMedia = emptyList()
                                        justSent = true
                                        runtime.sendMessage(spokenText, media)
                                    }
                                }
                            },
                            enabled = !isRunning,
                        )
                    }

                    val canSend = !isRunning && (inputText.isNotBlank() || pendingMedia.isNotEmpty())
                    IconButton(
                        onClick = {
                            if (canSend) {
                                val text = inputText.ifBlank { "Here are the files I'm sharing." }
                                val mediaItems = pendingMedia.toList()
                                inputText = ""
                                pendingMedia = emptyList()
                                justSent = true
                                scope.launch {
                                    val media = withContext(Dispatchers.IO) {
                                        mediaItems.mapNotNull { mediaItemToContentPart(context, it) }
                                    }
                                    runtime.sendMessage(text, media)
                                }
                            }
                        },
                        enabled = canSend,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send",
                            tint = if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                    }
                }
            }
        }
    }

    // Model picker bottom sheet
    if (showModelPicker && modelRouter != null) {
        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Select Model", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp))

                // Auto option
                val isAuto = runtime.preferredModelId.isNullOrBlank()
                Surface(
                    onClick = {
                        runtime.preferredModelId = null
                        showModelPicker = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isAuto) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Auto (best available)", style = MaterialTheme.typography.bodyMedium)
                            Text("Picks the fastest or most capable model", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (isAuto) {
                            Spacer(Modifier.weight(1f))
                            Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                for ((provider, model) in modelRouter.getAvailableModels()) {
                    val isSelected = runtime.preferredModelId == model.id
                    Surface(
                        onClick = {
                            runtime.preferredModelId = model.id
                            showModelPicker = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Memory, null, Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(buildString {
                                    append(provider.displayName)
                                    if (model.supportsVision) append(" · Vision")
                                    append(" · ${model.contextWindow / 1000}K context")
                                }, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onSuggestionClick: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val scale by infiniteTransition.animateFloat(
            initialValue = 0.95f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(1500, easing = EaseInOutCubic), RepeatMode.Reverse),
            label = "scale",
        )

        Icon(Icons.Default.AutoAwesome, null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp).scale(scale))
        Spacer(Modifier.height(16.dp))
        Text("OpenClaw", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("Your personal AI assistant.\nType a message, use voice, or share media from any app.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))

        SuggestedPrompts(onSuggestionClick = onSuggestionClick)
    }
}

@Composable
private fun LoadingIndicator(state: AgentState) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha",
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary)
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

private fun uriToContentPart(context: android.content.Context, mimeType: String, uri: Uri): ContentPart? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.use { it.readBytes() }

        // Skip files over 10MB
        if (bytes.size > 10 * 1024 * 1024) {
            return ContentPart(type = "text", text = "File too large (${bytes.size / 1024 / 1024}MB). Max 10MB.")
        }

        when {
            mimeType.startsWith("image/") -> {
                val compressed = compressImage(bytes)
                val base64 = Base64.encodeToString(compressed, Base64.NO_WRAP)
                ContentPart(type = "image_base64", mediaType = "image/jpeg", data = base64)
            }
            mimeType.startsWith("audio/") -> {
                val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ContentPart(type = "audio_base64", mediaType = mimeType, data = base64)
            }
            mimeType.startsWith("text/") -> ContentPart(type = "text", text = "Shared file content:\n${String(bytes)}")
            else -> ContentPart(type = "text", text = "Shared file: ${uri.lastPathSegment} (type: $mimeType, ${bytes.size} bytes)")
        }
    } catch (e: Exception) {
        ContentPart(type = "text", text = "Failed to read shared file: ${e.message}")
    }
}

private fun compressImage(bytes: ByteArray, maxDimension: Int = 1024, quality: Int = 85): ByteArray {
    val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
    val width = original.width
    val height = original.height

    // Only resize if larger than maxDimension
    val bitmap = if (width > maxDimension || height > maxDimension) {
        val scale = maxDimension.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        Bitmap.createScaledBitmap(original, newWidth, newHeight, true).also {
            if (it !== original) original.recycle()
        }
    } else {
        original
    }

    val outputStream = java.io.ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
    bitmap.recycle()
    return outputStream.toByteArray()
}

private fun mediaItemToContentPart(context: android.content.Context, item: MediaItem): ContentPart? {
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
