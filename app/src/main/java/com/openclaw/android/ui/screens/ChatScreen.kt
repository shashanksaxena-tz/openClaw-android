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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.agent.AgentEvent
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.agent.AgentState
import com.openclaw.android.llm.ContentPart
import com.openclaw.android.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

// ── Media items for pending attachments ─────────────────────────────────────

data class MediaItem(val mimeType: String, val uri: Uri)

fun mimeTypeToMediaItem(mimeType: String, uri: Uri) = MediaItem(mimeType, uri)

suspend fun mediaItemToContentPart(context: android.content.Context, item: MediaItem): ContentPart? =
    uriToContentPart(context, item.mimeType, item.uri)

suspend fun uriToContentPart(context: android.content.Context, mimeType: String, uri: Uri): ContentPart? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val bytes = inputStream.use { it.readBytes() }

        if (mimeType.startsWith("image/")) {
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
                val scale = 1024f / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt(),
                    (bitmap.height * scale).toInt(),
                    true,
                )
            } else bitmap

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
            val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            ContentPart(type = "image_base64", mediaType = "image/jpeg", data = base64)
        } else {
            val text = bytes.decodeToString()
            ContentPart(type = "text", text = "Shared file ($mimeType):\n$text")
        }
    } catch (e: Exception) { null }
}

// ── Main ChatScreen ────────────────────────────────────────────────────────────

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
    onNavigateToDashboard: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by runtime.state.collectAsState()
    val events by runtime.events.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showVoiceMode by remember { mutableStateOf(false) }
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
        val activeNet = cm?.activeNetwork
        val caps = activeNet?.let { cm.getNetworkCapabilities(it) }
        isOnline = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        onDispose { cm?.unregisterNetworkCallback(callback) }
    }

    // Handle initial shared content
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

    // Auto-scroll
    LaunchedEffect(events.size) {
        if (events.isNotEmpty()) {
            justSent = false
            val lastIndex = maxOf(0, listState.layoutInfo.totalItemsCount - 1)
            if (lastIndex > 0) listState.animateScrollToItem(lastIndex)
        }
    }

    LaunchedEffect(state) {
        if (state is AgentState.Running) justSent = false
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

    // Consolidate stream chunks into single events
    val displayEvents = remember(events) {
        val filtered = mutableListOf<AgentEvent>()
        val streamAccumulator = StringBuilder()
        var streaming = false

        for (event in events) {
            when (event) {
                is AgentEvent.StreamStart -> { streamAccumulator.setLength(0); streaming = true }
                is AgentEvent.StreamChunk -> streamAccumulator.append(event.chunk)
                is AgentEvent.StreamEnd -> streaming = false
                is AgentEvent.AssistantMessage -> { streamAccumulator.setLength(0); streaming = false; filtered.add(event) }
                is AgentEvent.TokenUsage -> { /* skip */ }
                else -> {
                    if (streaming && streamAccumulator.isNotEmpty()) {
                        filtered.add(AgentEvent.StreamChunk(streamAccumulator.toString()))
                        streamAccumulator.setLength(0)
                    }
                    filtered.add(event)
                }
            }
        }
        if (streaming && streamAccumulator.isNotEmpty()) {
            filtered.add(AgentEvent.StreamChunk(streamAccumulator.toString()))
        }
        filtered
    }

    val isRunning = state is AgentState.Running || state is AgentState.ExecutingTool || justSent

    // Snackbar on errors
    LaunchedEffect(events) {
        val lastEvent = events.lastOrNull()
        if (lastEvent is AgentEvent.Error) {
            snackbarHostState.showSnackbar(
                message = lastEvent.message ?: "Something went wrong. Please try again.",
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Export dialog
    if (showExportDialog && onExportChat != null) {
        var exportFilename by remember { mutableStateOf("conversation-${System.currentTimeMillis() / 1000}.md") }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp),
            title = { Text("Export Conversation", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        "Save this conversation to your workspace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = exportFilename,
                        onValueChange = { exportFilename = it },
                        label = { Text("Filename") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val success = onExportChat(exportFilename)
                            snackbarHostState.showSnackbar(
                                if (success) "Exported to workspace/$exportFilename" else "Export failed"
                            )
                            showExportDialog = false
                        }
                    },
                    enabled = exportFilename.isNotBlank(),
                ) { Text("Export", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Voice conversation mode
    if (showVoiceMode) {
        VoiceConversationScreen(
            runtime = runtime,
            onDismiss = { showVoiceMode = false },
        )
        return
    }

    // Scroll state for scroll-to-bottom indicator
    val showScrollToBottom by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible < totalItems - 2
        }
    }
    val isEmpty = displayEvents.isEmpty()

    // ── Main Layout ─────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(12.dp),
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.imePadding(),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Clean Top Bar ─────────────────────────────────────────
                CleanTopBar(
                    runtime = runtime,
                    modelRouter = modelRouter,
                    onOpenDrawer = onOpenDrawer,
                    onNavigateToSettings = onNavigateToSettings,
                    onNavigateToDashboard = onNavigateToDashboard,
                    onExportChat = if (onExportChat != null) { { showExportDialog = true } } else null,
                    onNewConversation = { scope.launch { runtime.startNewConversation() } },
                    onModelChipClick = {
                        if (modelRouter != null) showModelPicker = true else onNavigateToSettings()
                    },
                )

                // ── Offline Banner ────────────────────────────────────────
                val hasLocalModel = modelRouter?.let {
                    it.getAvailableModels().any { (provider, _) -> provider.providerId == "local-llama" }
                } ?: false
                AnimatedVisibility(
                    visible = !isOnline,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    OfflineBanner(hasLocalModel = hasLocalModel)
                }

                // ── Messages List ─────────────────────────────────────────
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 12.dp),
                    ) {
                        if (isEmpty) {
                            item {
                                WelcomeHero(
                                    modelName = runtime.activeModelName ?: "OpenClaw",
                                )
                            }
                        }
                        itemsIndexed(
                            items = displayEvents,
                            key = { index, event ->
                                when (event) {
                                    is AgentEvent.UserMessage -> "user-$index"
                                    is AgentEvent.AssistantMessage -> "assistant-$index"
                                    is AgentEvent.ToolCallStart -> "tool-start-$index"
                                    is AgentEvent.ToolCallResult -> "tool-result-$index"
                                    is AgentEvent.StreamChunk -> "stream-$index"
                                    is AgentEvent.ModelSelected -> "model-$index"
                                    is AgentEvent.Escalation -> "escalation-$index"
                                    is AgentEvent.Error -> "error-$index"
                                    else -> "event-$index"
                                }
                            },
                        ) { _, event ->
                            MessageBubble(
                                event = event,
                                onRetry = if (event is AgentEvent.Error) {
                                    { scope.launch { runtime.retryLastMessage() } }
                                } else null,
                            )
                        }

                        if (isRunning) {
                            item { ThinkingIndicator() }
                        }
                    }

                    // ── Scroll-to-bottom indicator ────────────────────────
                    if (showScrollToBottom) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val lastIndex = maxOf(0, listState.layoutInfo.totalItemsCount - 1)
                                    listState.animateScrollToItem(lastIndex)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                ),
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Scroll to bottom",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                // ── Suggested Prompts (bottom, only when empty) ───────────
                AnimatedVisibility(
                    visible = isEmpty,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    SuggestedPrompts(
                        onSuggestionClick = { prompt -> inputText = prompt },
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }

                // ── Pending Media Preview ─────────────────────────────────
                AnimatedVisibility(
                    visible = pendingMedia.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                ) {
                    MediaPreviewStrip(
                        media = pendingMedia,
                        onRemove = { index -> pendingMedia = pendingMedia.toMutableList().also { it.removeAt(index) } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }

                // ── Clean Input Bar ───────────────────────────────────────
                CleanInputBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    isRunning = isRunning,
                    onAttach = { filePickerLauncher.launch("image/*") },
                    onVoiceResult = { spokenText ->
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
                    onSend = {
                        val canSend = !isRunning && (inputText.isNotBlank() || pendingMedia.isNotEmpty())
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
                    onStop = { runtime.cancel() },
                )
            }
        }
    }

    // ── Model Picker Bottom Sheet ──────────────────────────────────────────
    if (showModelPicker && modelRouter != null) {
        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
            containerColor = MaterialTheme.colorScheme.surface,
            scrimColor = Color.Black.copy(alpha = 0.3f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .size(36.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                Text(
                    "Select Model",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                val isAuto = runtime.preferredModelId.isNullOrBlank()
                ModelPickerCard(
                    title = "Auto (best available)",
                    subtitle = "Picks the fastest or most capable model",
                    isSelected = isAuto,
                    onClick = {
                        runtime.preferredModelId = null
                        showModelPicker = false
                    },
                )

                for ((provider, model) in modelRouter.getAvailableModels()) {
                    val isSelected = runtime.preferredModelId == model.id
                    ModelPickerCard(
                        title = model.displayName,
                        subtitle = buildString {
                            append(provider.displayName)
                            if (model.supportsVision) append(" \u00B7 Vision")
                            append(" \u00B7 ${model.contextWindow / 1000}K context")
                        },
                        isSelected = isSelected,
                        onClick = {
                            runtime.preferredModelId = model.id
                            showModelPicker = false
                        },
                    )
                }
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Clean Top Bar ────────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun CleanTopBar(
    runtime: AgentRuntime,
    modelRouter: com.openclaw.android.llm.ModelRouter?,
    onOpenDrawer: (() -> Unit)?,
    onNavigateToSettings: () -> Unit,
    onNavigateToDashboard: (() -> Unit)?,
    onExportChat: (() -> Unit)?,
    onNewConversation: () -> Unit,
    onModelChipClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left pill: Settings + Chat history
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Settings, contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (onOpenDrawer != null) {
                    IconButton(onClick = onOpenDrawer, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.ChatBubbleOutline, contentDescription = "History",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Center: Model name + size badge + chevron
            val fullModelName = runtime.activeModelName ?: "Auto"
            val sizePattern = Regex("\\b(\\d+\\.?\\d*[BbMm]|E\\d+[BbMm])\\b")
            val sizeMatch = sizePattern.find(fullModelName)
            val displayName = if (sizeMatch != null) {
                fullModelName.substring(0, sizeMatch.range.first).trimEnd()
            } else fullModelName
            val sizeBadge = sizeMatch?.value

            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onModelChipClick,
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = buildAnnotatedString {
                        append(displayName)
                        if (sizeBadge != null) {
                            append(" ")
                            withStyle(SpanStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Normal,
                            )) {
                                append(sizeBadge)
                            }
                        }
                    },
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Change model",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Right: New conversation (compose icon)
            IconButton(onClick = onNewConversation, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Edit, contentDescription = "New conversation",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Welcome Hero (gradient background + model name) ─────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun WelcomeHero(
    modelName: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        com.openclaw.android.ui.theme.WelcomeGradientStart,
                        com.openclaw.android.ui.theme.WelcomeGradientEnd,
                    ),
                ),
            )
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Meet $modelName",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                ),
                color = Color(0xFF1C1C1E),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Your personal AI assistant running on-device and in the cloud. Ask anything.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF3C3C43),
                modifier = Modifier.padding(horizontal = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Thinking Indicator ───────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(3) { i ->
                val dotAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = i * 150, easing = EaseInOutSine),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot-$i",
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            CircleShape,
                        ),
                )
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Offline Banner ───────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun OfflineBanner(hasLocalModel: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.WifiOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (hasLocalModel) "Offline \u2014 using on-device model"
            else "Offline \u2014 connect to use AI",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Clean Input Bar ──────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun CleanInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isRunning: Boolean,
    onAttach: () -> Unit,
    onVoiceResult: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val canSend = inputText.isNotBlank() && !isRunning

    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Attach button
            IconButton(
                onClick = onAttach,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Text field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        text = "Ask anything",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Right: Send / Stop / Voice
            if (isRunning) {
                IconButton(
                    onClick = onStop,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else if (canSend) {
                IconButton(
                    onClick = onSend,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                // Voice input — matches reference equalizer icon
                VoiceInputButton(
                    onResult = onVoiceResult,
                    enabled = !isRunning,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Media Preview Strip ──────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
fun MediaPreviewStrip(
    media: List<MediaItem>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        media.forEachIndexed { index, item ->
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
                IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Model Picker Card ────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun ModelPickerCard(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
