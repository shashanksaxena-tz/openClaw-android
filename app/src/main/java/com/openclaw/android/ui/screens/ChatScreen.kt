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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ── Design Tokens ──────────────────────────────────────────────────────────────
private val TrueBlack = Color(0xFF050508)
private val Charcoal = Color(0xFF0D0D12)
private val CharcoalLight = Color(0xFF16161D)
private val ElectricViolet = Color(0xFFA855F7)
private val NeonCyan = Color(0xFF22D3EE)
private val HotPink = Color(0xFFEC4899)
private val MutedGray = Color(0xFF6B7280)
private val SubtleGray = Color(0xFF374151)
private val OffWhite = Color(0xFFE5E7EB)
private val DimWhite = Color(0xFF9CA3AF)
private val ErrorRed = Color(0xFFEF4444)
private val GlassBg = Color(0xFF0D0D12).copy(alpha = 0.65f)
private val GlassBorder = Color.White.copy(alpha = 0.08f)

private val VioletPinkGradient = Brush.linearGradient(listOf(ElectricViolet, HotPink))
private val VioletCyanGradient = Brush.linearGradient(listOf(ElectricViolet, NeonCyan))
private val VioletTextGradient = Brush.horizontalGradient(listOf(ElectricViolet, HotPink))

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
            val lastIndex = maxOf(0, listState.layoutInfo.totalItemsCount - 1)
            if (lastIndex > 0) listState.animateScrollToItem(lastIndex)
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

    // Filter display events — accumulate stream chunks into a single synthetic event
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

    // ── Export Dialog (Glass Styled) ───────────────────────────────────────────
    if (showExportDialog && onExportChat != null) {
        var exportFilename by remember { mutableStateOf("conversation-${System.currentTimeMillis() / 1000}.md") }
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            containerColor = Charcoal,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ElectricViolet.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.FileDownload, null,
                        tint = NeonCyan,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            title = {
                Text(
                    "Export Conversation",
                    color = OffWhite,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Column {
                    Text(
                        "Save this conversation to your workspace.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = DimWhite,
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = exportFilename,
                        onValueChange = { exportFilename = it },
                        label = { Text("Filename", color = MutedGray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = OffWhite),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = SubtleGray,
                            cursorColor = NeonCyan,
                            focusedLabelColor = NeonCyan,
                        ),
                        shape = RoundedCornerShape(14.dp),
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
                    colors = ButtonDefaults.textButtonColors(contentColor = NeonCyan),
                ) {
                    Text("Export", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MutedGray),
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // ── Voice Conversation Mode ───────────────────────────────────────────────
    if (showVoiceMode) {
        VoiceConversationScreen(
            runtime = runtime,
            onDismiss = { showVoiceMode = false },
        )
        return
    }

    // ── Main Layout ───────────────────────────────────────────────────────────
    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = CharcoalLight,
                    contentColor = OffWhite,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
        containerColor = TrueBlack,
        modifier = modifier.imePadding(),
    ) { scaffoldPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .background(TrueBlack),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Glass Top Bar ─────────────────────────────────────────
                GlassTopBar(
                    runtime = runtime,
                    modelRouter = modelRouter,
                    events = events,
                    onOpenDrawer = onOpenDrawer,
                    onNavigateToSettings = onNavigateToSettings,
                    onExportChat = if (onExportChat != null) { { showExportDialog = true } } else null,
                    onNewConversation = { scope.launch { runtime.startNewConversation() } },
                    onClearChat = { scope.launch { runtime.clearConversation() } },
                    onModelChipClick = {
                        if (modelRouter != null) showModelPicker = true else onNavigateToSettings()
                    },
                )

                // ── Offline Banner (Glass + Red Glow) ─────────────────────
                AnimatedVisibility(
                    visible = !isOnline,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    OfflineBanner()
                }

                // ── Messages List ─────────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp),
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
                        item { LoadingIndicator(state) }
                    }
                }

                // ── Pending Media Preview Strip ───────────────────────────
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

                // ── Floating Glass Input Bar ──────────────────────────────
                FloatingInputBar(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    isRunning = isRunning,
                    pendingMedia = pendingMedia,
                    onAttach = { filePickerLauncher.launch("image/*") },
                    onVoiceMode = { showVoiceMode = true },
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

    // ── Model Picker Bottom Sheet (Glass Styled) ──────────────────────────────
    if (showModelPicker && modelRouter != null) {
        ModalBottomSheet(
            onDismissRequest = { showModelPicker = false },
            containerColor = Charcoal,
            scrimColor = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .size(40.dp, 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SubtleGray),
                )
            },
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)) {
                Text(
                    "Select Model",
                    style = MaterialTheme.typography.titleMedium,
                    color = OffWhite,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 16.dp),
                )

                // Auto option
                val isAuto = runtime.preferredModelId.isNullOrBlank()
                ModelPickerCard(
                    icon = Icons.Default.AutoAwesome,
                    iconTint = ElectricViolet,
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
                        icon = Icons.Default.Memory,
                        iconTint = if (isSelected) NeonCyan else MutedGray,
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
// ── Glass Top Bar ──────────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun GlassTopBar(
    runtime: AgentRuntime,
    modelRouter: com.openclaw.android.llm.ModelRouter?,
    events: List<AgentEvent>,
    onOpenDrawer: (() -> Unit)?,
    onNavigateToSettings: () -> Unit,
    onExportChat: (() -> Unit)?,
    onNewConversation: () -> Unit,
    onClearChat: () -> Unit,
    onModelChipClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Charcoal.copy(alpha = 0.85f),
                        Charcoal.copy(alpha = 0.4f),
                        Color.Transparent,
                    )
                )
            )
            .drawBehind {
                // Subtle bottom edge glow line
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, ElectricViolet.copy(alpha = 0.3f), Color.Transparent)
                    ),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Hamburger menu
            if (onOpenDrawer != null) {
                GlowIconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Default.Menu, contentDescription = "Open drawer", tint = OffWhite)
                }
            }

            // "OpenClaw" gradient text
            Text(
                "OpenClaw",
                style = MaterialTheme.typography.titleLarge.copy(
                    brush = VioletTextGradient,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                ),
                modifier = Modifier.padding(start = if (onOpenDrawer != null) 0.dp else 12.dp),
            )

            // Space chip
            runtime.activeSpaceName?.let { spaceName ->
                Spacer(Modifier.width(8.dp))
                GlassChip(label = spaceName)
            }

            // Model indicator chip
            Spacer(Modifier.width(8.dp))
            val currentModelName = runtime.activeModelName ?: "Auto"
            GlassChip(
                label = currentModelName,
                leadingIcon = {
                    Icon(
                        Icons.Default.Memory, contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = NeonCyan,
                    )
                },
                onClick = onModelChipClick,
                accentBorder = true,
            )

            Spacer(Modifier.weight(1f))

            // Action icons
            if (onExportChat != null && events.isNotEmpty()) {
                GlowIconButton(onClick = onExportChat) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export chat",
                        tint = DimWhite, modifier = Modifier.size(20.dp))
                }
            }

            GlowIconButton(onClick = onNewConversation) {
                Icon(Icons.Default.Add, contentDescription = "New conversation",
                    tint = DimWhite, modifier = Modifier.size(20.dp))
            }

            GlowIconButton(onClick = onClearChat) {
                Icon(Icons.Default.ClearAll, contentDescription = "Clear chat",
                    tint = DimWhite, modifier = Modifier.size(20.dp))
            }
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Glass Chip ─────────────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun GlassChip(
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    accentBorder: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val borderColor = if (accentBorder && isPressed) NeonCyan.copy(alpha = 0.6f) else GlassBorder

    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassBg)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ) else Modifier
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(4.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = DimWhite,
        )
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Glow Icon Button (subtle glow on press) ───────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun GlowIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val glowAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.25f else 0f,
        animationSpec = tween(150),
        label = "glow",
    )

    IconButton(
        onClick = onClick,
        modifier = modifier.drawBehind {
            if (glowAlpha > 0f) {
                drawCircle(
                    color = ElectricViolet.copy(alpha = glowAlpha),
                    radius = size.minDimension * 0.6f,
                )
            }
        },
        interactionSource = interactionSource,
    ) {
        content()
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Offline Banner ─────────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun OfflineBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Red glow along top edge
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(ErrorRed.copy(alpha = 0.3f), Color.Transparent),
                        startY = 0f,
                        endY = size.height,
                    ),
                    size = Size(size.width, 4.dp.toPx()),
                )
            }
            .background(GlassBg)
            .border(width = 0.5.dp, color = ErrorRed.copy(alpha = 0.2f), shape = RoundedCornerShape(0.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.WifiOff, null,
                Modifier.size(16.dp),
                tint = ErrorRed.copy(alpha = 0.9f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "You're offline. Check your connection.",
                style = MaterialTheme.typography.labelMedium,
                color = ErrorRed.copy(alpha = 0.9f),
            )
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Empty State ────────────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun EmptyState(onSuggestionClick: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Background floating particles
        FloatingDots(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Animated AI Claw Icon (Canvas drawn)
            AnimatedClawIcon(
                modifier = Modifier.size(88.dp),
            )

            Spacer(Modifier.height(24.dp))

            // Gradient headline
            Text(
                "What can I help you with?",
                style = MaterialTheme.typography.headlineSmall.copy(
                    brush = VioletCyanGradient,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                "Your executive assistant for tasks, travel, team,\ncalendar, notes, decisions, and productivity.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(28.dp))

            SuggestedPrompts(onSuggestionClick = onSuggestionClick)
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Animated Claw Icon (Canvas) ────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun AnimatedClawIcon(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "claw")

    // Slow rotation for the outer ring
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "rotation",
    )

    // Pulsing scale for the core
    val coreScale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "coreScale",
    )

    // Glow pulse
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.45f,
        animationSpec = infiniteRepeatable(tween(2500, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "glow",
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        // Outer glow
        drawCircle(
            color = ElectricViolet.copy(alpha = glowAlpha),
            radius = radius * 1.1f,
            center = center,
        )

        // Rotating dashed ring
        rotate(rotation, center) {
            val arcStroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            for (i in 0 until 6) {
                val startAngle = i * 60f
                drawArc(
                    color = ElectricViolet.copy(alpha = 0.5f),
                    startAngle = startAngle,
                    sweepAngle = 30f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.85f, center.y - radius * 0.85f),
                    size = Size(radius * 1.7f, radius * 1.7f),
                    style = arcStroke,
                )
            }
        }

        // Inner filled circle (core)
        val scaledCoreRadius = radius * 0.5f * coreScale
        drawCircle(
            brush = Brush.radialGradient(
                listOf(NeonCyan.copy(alpha = 0.4f), ElectricViolet.copy(alpha = 0.2f), Color.Transparent),
                center = center,
                radius = scaledCoreRadius * 1.5f,
            ),
            radius = scaledCoreRadius,
            center = center,
        )

        // Claw paths - three arcs emanating from core
        val clawStroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        for (i in 0 until 3) {
            val angle = (i * 120f + rotation * 0.2f)
            val rad = Math.toRadians(angle.toDouble())
            val tipX = center.x + cos(rad).toFloat() * radius * 0.7f
            val tipY = center.y + sin(rad).toFloat() * radius * 0.7f

            val midX = center.x + cos(rad).toFloat() * radius * 0.35f
            val midY = center.y + sin(rad).toFloat() * radius * 0.35f

            drawLine(
                color = NeonCyan.copy(alpha = 0.8f),
                start = Offset(midX, midY),
                end = Offset(tipX, tipY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Small dot at tip
            drawCircle(
                color = NeonCyan,
                radius = 3.dp.toPx(),
                center = Offset(tipX, tipY),
            )
        }

        // Center dot
        drawCircle(
            color = Color.White,
            radius = 3.dp.toPx(),
            center = center,
        )
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Floating Dots ────────────────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

private data class FloatingDot(
    val xFraction: Float,
    val yFraction: Float,
    val radius: Float,
    val speed: Float,
    val color: Color,
)

@Composable
private fun FloatingDots(modifier: Modifier = Modifier) {
    val particles = remember {
        List(18) {
            FloatingDot(
                xFraction = Random.nextFloat(),
                yFraction = Random.nextFloat(),
                radius = Random.nextFloat() * 2f + 0.5f,
                speed = Random.nextFloat() * 0.4f + 0.2f,
                color = if (Random.nextBoolean()) ElectricViolet.copy(alpha = Random.nextFloat() * 0.15f + 0.05f)
                else NeonCyan.copy(alpha = Random.nextFloat() * 0.1f + 0.03f),
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(60000, easing = LinearEasing)),
        label = "time",
    )

    Canvas(modifier = modifier) {
        particles.forEach { p ->
            val xOffset = sin((time * p.speed * 0.01f + p.xFraction * 10f).toDouble()).toFloat() * 30f
            val yOffset = cos((time * p.speed * 0.008f + p.yFraction * 10f).toDouble()).toFloat() * 20f
            val x = p.xFraction * size.width + xOffset
            val y = p.yFraction * size.height + yOffset
            drawCircle(
                color = p.color,
                radius = p.radius.dp.toPx(),
                center = Offset(x, y),
            )
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Loading Indicator (Bouncing Dots) ──────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun LoadingIndicator(state: AgentState) {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")

    // Three dots with staggered bounce
    val dot1Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -8f,
        animationSpec = infiniteRepeatable(
            tween(500, easing = EaseInOutCubic),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(0),
        ),
        label = "dot1",
    )
    val dot2Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -8f,
        animationSpec = infiniteRepeatable(
            tween(500, easing = EaseInOutCubic),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(150),
        ),
        label = "dot2",
    )
    val dot3Offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = -8f,
        animationSpec = infiniteRepeatable(
            tween(500, easing = EaseInOutCubic),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(300),
        ),
        label = "dot3",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Glass pill container
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(GlassBg)
                .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bouncing dots
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(dot1Offset, dot2Offset, dot3Offset).forEach { offset ->
                    Box(
                        modifier = Modifier
                            .offset(y = offset.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet),
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Text(
                text = when (state) {
                    is AgentState.ExecutingTool -> "Running ${state.toolName}..."
                    else -> "Thinking..."
                },
                style = MaterialTheme.typography.labelMedium,
                color = when (state) {
                    is AgentState.ExecutingTool -> NeonCyan
                    else -> DimWhite
                },
            )
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Floating Glass Input Bar ───────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun FloatingInputBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    isRunning: Boolean,
    pendingMedia: List<MediaItem>,
    onAttach: () -> Unit,
    onVoiceMode: () -> Unit,
    onVoiceResult: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val canSend = !isRunning && (inputText.isNotBlank() || pendingMedia.isNotEmpty())
    var isFocused by remember { mutableStateOf(false) }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val inputBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val inputBorderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f)

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else inputBorderColor,
        animationSpec = tween(300),
        label = "inputBorder",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(inputBg)
                .border(1.dp, borderColor, RoundedCornerShape(28.dp))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Attach button with scale animation
            val attachInteraction = remember { MutableInteractionSource() }
            val attachPressed by attachInteraction.collectIsPressedAsState()
            val attachScale by animateFloatAsState(
                if (attachPressed) 0.85f else 1f, tween(100), label = "attachScale"
            )

            IconButton(
                onClick = onAttach,
                modifier = Modifier.size(40.dp).scale(attachScale),
                interactionSource = attachInteraction,
            ) {
                Icon(
                    Icons.Default.AttachFile, "Attach",
                    tint = MutedGray,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Text input field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        "Ask anything",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MutedGray.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    maxLines = 15,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                )
            }

            Spacer(Modifier.width(4.dp))

            // ── Action buttons (consistent 36-40 dp, evenly spaced) ──
            if (isRunning) {
                StopButton(onClick = onStop)
                Spacer(Modifier.width(4.dp))
            } else {
                VoiceModeButton(onClick = onVoiceMode)
                Spacer(Modifier.width(4.dp))
                VoiceInputButton(
                    onResult = onVoiceResult,
                    enabled = !isRunning,
                )
                Spacer(Modifier.width(4.dp))
            }

            SendButton(
                canSend = canSend,
                onClick = onSend,
            )
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Send Button (Gradient) ─────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SendButton(canSend: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        if (isPressed) 0.88f else 1f, tween(100), label = "sendScale"
    )

    Box(
        modifier = Modifier
            .size(40.dp)
            .scale(buttonScale)
            .clip(CircleShape)
            .then(
                if (canSend) Modifier.background(VioletPinkGradient)
                else Modifier.background(SubtleGray.copy(alpha = 0.5f))
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = canSend,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Send, "Send",
            tint = if (canSend) Color.White else MutedGray,
            modifier = Modifier.size(18.dp),
        )
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Stop Button (Red Pulse) ────────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun StopButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "stopPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutCubic), RepeatMode.Reverse),
        label = "stopScale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (isPressed) 0.85f else 1f, tween(100), label = "stopPress"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(pulseScale * pressScale)
            .clip(CircleShape)
            .background(ErrorRed.copy(alpha = 0.2f))
            .border(1.5.dp, ErrorRed.copy(alpha = 0.6f), CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Stop, "Stop",
            tint = ErrorRed,
            modifier = Modifier.size(18.dp),
        )
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Voice Mode Button (Small with Pulse) ───────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun VoiceModeButton(onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.0f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "voiceAlpha",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (isPressed) 0.85f else 1f, tween(100), label = "voicePress"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(pressScale)
            .clip(CircleShape)
            .drawBehind {
                drawCircle(
                    color = ElectricViolet.copy(alpha = pulseAlpha),
                    radius = size.minDimension * 0.6f,
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.PhoneInTalk, "Voice mode",
            tint = ElectricViolet,
            modifier = Modifier.size(18.dp),
        )
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Model Picker Card (Glass) ──────────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun ModelPickerCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) ElectricViolet.copy(alpha = 0.1f) else CharcoalLight
    val borderCol = if (isSelected) ElectricViolet.copy(alpha = 0.4f) else GlassBorder

    // Glow when selected
    val glowAlpha by animateFloatAsState(
        targetValue = if (isSelected) 0.15f else 0f,
        animationSpec = tween(300),
        label = "modelGlow",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawRoundRect(
                        color = ElectricViolet.copy(alpha = glowAlpha),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
                    )
                }
            }
            .background(bgColor, RoundedCornerShape(14.dp))
            .border(0.5.dp, borderCol, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = iconTint)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = OffWhite)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MutedGray,
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle, null,
                Modifier.size(20.dp),
                tint = NeonCyan,
            )
        }
    }
}


// ════════════════════════════════════════════════════════════════════════════════
// ── Helper Functions (unchanged) ───────────────────────────────────────────────
// ════════════════════════════════════════════════════════════════════════════════

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
