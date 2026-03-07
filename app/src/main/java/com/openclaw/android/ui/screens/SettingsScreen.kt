package com.openclaw.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.openclaw.android.agent.AgentRuntime
import com.openclaw.android.data.ConversationExpiry
import com.openclaw.android.data.PrivacyAudit
import com.openclaw.android.data.SettingsRepository
import com.openclaw.android.data.Space
import com.openclaw.android.data.SpaceManager
import com.openclaw.android.llm.DownloadState
import com.openclaw.android.llm.DownloadableModel
import com.openclaw.android.llm.ModelDownloadManager
import com.openclaw.android.llm.ModelRouter
import com.openclaw.android.llm.ModelTier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ─── Design tokens ───────────────────────────────────────────────────────────
private val BgBlack = Color(0xFF050508)
private val SurfaceCharcoal = Color(0xFF0D0D12)
private val PrimaryViolet = Color(0xFFA855F7)
private val SecondaryCyan = Color(0xFF22D3EE)
private val TertiaryPink = Color(0xFFEC4899)
private val TextPrimary = Color(0xFFEEEEF0)
private val TextSecondary = Color(0xFF9293A0)
private val TextMuted = Color(0xFF8B8C9A)
private val GlassBg = Color(0xFF0D0D12).copy(alpha = 0.62f)
private val GlassBorder = Color.White.copy(alpha = 0.06f)
private val InputBg = Color(0xFF09090C)
private val ErrorRed = Color(0xFFEF4444)
private val SuccessGreen = Color(0xFF22C55E)
private val GlassShape = RoundedCornerShape(20.dp)
private val InnerShape = RoundedCornerShape(14.dp)
private val PillShape = RoundedCornerShape(50)

private val VioletCyanGradient = Brush.linearGradient(
    colors = listOf(PrimaryViolet, SecondaryCyan),
)
private val VioletPinkGradient = Brush.linearGradient(
    colors = listOf(PrimaryViolet, TertiaryPink),
)

// ─── Main Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsRepository,
    modelRouter: ModelRouter,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    spaceManager: SpaceManager? = null,
    agentRuntime: AgentRuntime? = null,
    conversationExpiry: ConversationExpiry? = null,
    privacyAudit: PrivacyAudit? = null,
    onNavigateToFiles: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var geminiKey by remember { mutableStateOf(settings.getGeminiKey()) }
    var groqKey by remember { mutableStateOf(settings.getGroqKey()) }
    var cerebrasKey by remember { mutableStateOf(settings.getCerebrasKey()) }
    var selectedModel by remember { mutableStateOf(settings.getDefaultModel()) }
    var systemPrompt by remember { mutableStateOf(settings.getSystemPrompt()) }
    var promptSaveJob by remember { mutableStateOf<Job?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showCreateSpace by remember { mutableStateOf(false) }
    var spaces by remember { mutableStateOf(spaceManager?.getSpaces() ?: emptyList()) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgBlack),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // ── Header ───────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button
                GlassIconButton(onClick = onBack, icon = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")

                Spacer(Modifier.width(14.dp))

                // "Settings" gradient title
                Text(
                    text = "Settings",
                    style = TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        brush = VioletCyanGradient,
                    ),
                )

                Spacer(Modifier.weight(1f))

                // Version badge
                Box(
                    modifier = Modifier
                        .background(GlassBg, PillShape)
                        .border(0.5.dp, GlassBorder, PillShape)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(
                        "v0.5.0",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SecondaryCyan,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── API Keys Section ─────────────────────────────────────────────
            GlassSection(
                icon = Icons.Outlined.Key,
                title = "API Keys",
                description = "Encrypted on device. Never sent anywhere except to the provider.",
            ) {
                DebouncedApiKeyField(
                    label = "Gemini API Key",
                    value = geminiKey,
                    onValueChange = { geminiKey = it },
                    onSave = { settings.setGeminiKey(it) },
                    hint = "Free: 250-1500 req/day, vision, 1M context",
                    getKeyUrl = "https://aistudio.google.com/apikey",
                    validateFormat = { it.length >= 30 },
                )
                Spacer(Modifier.height(14.dp))

                DebouncedApiKeyField(
                    label = "Groq API Key",
                    value = groqKey,
                    onValueChange = { groqKey = it },
                    onSave = { settings.setGroqKey(it) },
                    hint = "Free: fast inference, open-source models",
                    getKeyUrl = "https://console.groq.com/keys",
                    validateFormat = { it.startsWith("gsk_") },
                )
                Spacer(Modifier.height(14.dp))

                DebouncedApiKeyField(
                    label = "Cerebras API Key",
                    value = cerebrasKey,
                    onValueChange = { cerebrasKey = it },
                    onSave = { settings.setCerebrasKey(it) },
                    hint = "Free: fastest inference speed",
                    getKeyUrl = "https://cloud.cerebras.ai/",
                    validateFormat = { it.length >= 20 },
                )
            }

            Spacer(Modifier.height(18.dp))

            // ── Model Selection Section ──────────────────────────────────────
            GlassSection(
                icon = Icons.Outlined.Psychology,
                title = "Default Model",
                description = "Choose the AI model used for conversations.",
            ) {
                val availableModels = modelRouter.getAvailableModels()
                val currentModel = availableModels.find { (_, info) -> info.id == selectedModel }

                // Current selection card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(InnerShape)
                        .background(InputBg)
                        .border(
                            width = 0.5.dp,
                            brush = if (currentModel != null) VioletCyanGradient
                            else Brush.linearGradient(listOf(GlassBorder, GlassBorder)),
                            shape = InnerShape,
                        )
                        .clickable { showModelPicker = true }
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Icon
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(
                                    PrimaryViolet.copy(alpha = 0.12f),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selectedModel.isBlank()) {
                                SparkleIcon()
                            } else {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = PrimaryViolet,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                currentModel?.second?.displayName ?: "Auto (best available)",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                ),
                            )
                            if (currentModel != null) {
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        currentModel.first.displayName,
                                        style = TextStyle(fontSize = 12.sp, color = TextMuted),
                                    )
                                    if (currentModel.second.supportsVision) {
                                        Spacer(Modifier.width(6.dp))
                                        MiniPill("Vision", SecondaryCyan)
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "${currentModel.second.contextWindow / 1000}K",
                                        style = TextStyle(fontSize = 11.sp, color = TextMuted),
                                    )
                                }
                            } else {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Picks the best available model for each task",
                                    style = TextStyle(fontSize = 12.sp, color = TextMuted),
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Model picker dialog
            if (showModelPicker) {
                val availableModels = modelRouter.getAvailableModels()
                ModelPickerDialog(
                    availableModels = availableModels,
                    selectedModelId = selectedModel,
                    onSelect = { modelId ->
                        selectedModel = modelId
                        settings.setDefaultModel(modelId)
                        showModelPicker = false
                    },
                    onDismiss = { showModelPicker = false },
                )
            }

            // ── Local Models Section ──────────────────────────────────────────
            run {
                val app = context.applicationContext as? com.openclaw.android.OpenClawApp
                val downloadManager = app?.modelDownloadManager
                if (downloadManager != null) {
                    Spacer(Modifier.height(18.dp))
                    LocalModelsSection(
                        downloadManager = downloadManager,
                        settings = settings,
                    )
                }
            }

            // ── Spaces Section ───────────────────────────────────────────────
            if (spaceManager != null && agentRuntime != null) {
                Spacer(Modifier.height(18.dp))

                GlassSection(
                    icon = Icons.Outlined.Workspaces,
                    title = "Spaces",
                    description = "Organize projects with separate files and AI context.",
                    trailing = {
                        // Create button pill
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(PrimaryViolet.copy(alpha = 0.12f))
                                .border(0.5.dp, PrimaryViolet.copy(alpha = 0.25f), PillShape)
                                .clickable { showCreateSpace = true }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = PrimaryViolet,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "New",
                                    style = TextStyle(
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryViolet,
                                    ),
                                )
                            }
                        }
                    },
                ) {
                    val activeSpaceName = agentRuntime.activeSpaceName

                    for ((index, space) in spaces.withIndex()) {
                        if (index > 0) Spacer(Modifier.height(8.dp))

                        val isActive = activeSpaceName?.contains(space.name) == true

                        SpaceCard(
                            space = space,
                            isActive = isActive,
                            onClick = {
                                if (isActive) agentRuntime.setActiveSpace(null)
                                else {
                                    agentRuntime.setActiveSpace(space.id)
                                    scope.launch { agentRuntime.startNewConversation() }
                                }
                            },
                            onDelete = if (space.id != "default") {
                                {
                                    spaceManager.deleteSpace(space.id)
                                    if (isActive) agentRuntime.setActiveSpace(null)
                                    spaces = spaceManager.getSpaces()
                                }
                            } else null,
                        )
                    }
                }

                if (showCreateSpace) {
                    CreateSpaceDialog(
                        onDismiss = { showCreateSpace = false },
                        onCreate = { name, emoji, desc ->
                            spaceManager.createSpace(name, emoji, desc)
                            spaces = spaceManager.getSpaces()
                            showCreateSpace = false
                        },
                    )
                }
            }

            // ── File Browser Section ──────────────────────────────────────────
            if (onNavigateToFiles != null) {
                Spacer(Modifier.height(18.dp))

                GlassSection(
                    icon = Icons.Outlined.Folder,
                    title = "File Browser",
                    description = "Browse and manage your workspace files.",
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(InnerShape)
                            .background(InputBg)
                            .border(0.5.dp, GlassBorder, InnerShape)
                            .clickable(onClick = onNavigateToFiles)
                            .padding(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = PrimaryViolet, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Open Files", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary))
                                Text("View shared files and workspace", style = TextStyle(fontSize = 12.sp, color = TextMuted))
                            }
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextMuted, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // ── Conversation Expiry Section ────────────────────────────────────
            if (conversationExpiry != null) {
                Spacer(Modifier.height(18.dp))

                var selectedExpiry by remember { mutableIntStateOf(conversationExpiry.expiryDays) }
                val expiryOptions = conversationExpiry.getOptions()

                GlassSection(
                    icon = Icons.Outlined.AutoDelete,
                    title = "Conversation Expiry",
                    description = "Auto-delete old conversations to save space and protect privacy.",
                ) {
                    // Split into two rows to avoid overflow
                    for (rowOptions in expiryOptions.chunked(4)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            for ((days, label) in rowOptions) {
                                val isSelected = selectedExpiry == days
                                Box(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(
                                            if (isSelected) PrimaryViolet.copy(alpha = 0.15f)
                                            else InputBg,
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.5.dp,
                                            color = if (isSelected) PrimaryViolet.copy(alpha = 0.5f) else GlassBorder,
                                            shape = PillShape,
                                        )
                                        .clickable {
                                            selectedExpiry = days
                                            conversationExpiry.expiryDays = days
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        label,
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) PrimaryViolet else TextSecondary,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Privacy Audit Section ─────────────────────────────────────────
            if (privacyAudit != null) {
                Spacer(Modifier.height(18.dp))

                val summary = remember { privacyAudit.getSummary() }
                val dateFormat = remember { java.text.SimpleDateFormat("MMM d, h:mm a", java.util.Locale.getDefault()) }

                GlassSection(
                    icon = Icons.Outlined.Shield,
                    title = "Privacy Audit",
                    description = "See what data has been sent and where.",
                ) {
                    // Stats row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Total calls
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(InnerShape)
                                .background(InputBg)
                                .border(0.5.dp, GlassBorder, InnerShape)
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    "${summary.totalCalls}",
                                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = SecondaryCyan),
                                )
                                Text("API calls", style = TextStyle(fontSize = 11.sp, color = TextMuted))
                            }
                        }
                        // Providers
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(InnerShape)
                                .background(InputBg)
                                .border(0.5.dp, GlassBorder, InnerShape)
                                .padding(12.dp),
                        ) {
                            Column {
                                Text(
                                    "${summary.byProvider.size}",
                                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PrimaryViolet),
                                )
                                Text("Providers used", style = TextStyle(fontSize = 11.sp, color = TextMuted))
                            }
                        }
                    }

                    if (summary.byProvider.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        for ((provider, count) in summary.byProvider) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(provider.replaceFirstChar { it.uppercase() }, style = TextStyle(fontSize = 13.sp, color = TextPrimary))
                                Text("$count calls", style = TextStyle(fontSize = 13.sp, color = TextMuted))
                            }
                        }
                    }

                    if (summary.lastCall > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Last activity: ${dateFormat.format(java.util.Date(summary.lastCall))}",
                            style = TextStyle(fontSize = 11.sp, color = TextMuted),
                        )
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "All data encrypted on-device. No telemetry collected.",
                        style = TextStyle(fontSize = 11.sp, color = SuccessGreen.copy(alpha = 0.7f)),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── System Prompt Section ────────────────────────────────────────
            GlassSection(
                icon = Icons.Outlined.Terminal,
                title = "System Prompt",
                description = "Customize the AI's behavior and personality.",
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp)
                        .clip(InnerShape)
                        .background(InputBg)
                        .border(0.5.dp, GlassBorder, InnerShape)
                        .padding(14.dp),
                ) {
                    BasicTextField(
                        value = systemPrompt,
                        onValueChange = {
                            systemPrompt = it
                            promptSaveJob?.cancel()
                            promptSaveJob = scope.launch {
                                delay(500)
                                settings.setSystemPrompt(it)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontSize = 13.sp,
                            color = TextPrimary,
                            lineHeight = 20.sp,
                        ),
                        cursorBrush = SolidColor(PrimaryViolet),
                        decorationBox = { innerTextField ->
                            if (systemPrompt.isEmpty()) {
                                Text(
                                    "Enter system prompt...",
                                    style = TextStyle(fontSize = 13.sp, color = TextMuted),
                                )
                            }
                            innerTextField()
                        },
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // ── Footer ───────────────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "OpenClaw Android v0.5.0",
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        brush = VioletCyanGradient,
                    ),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Powered by AI",
                    style = TextStyle(
                        fontSize = 11.sp,
                        color = TextMuted,
                    ),
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Glass Section Card ──────────────────────────────────────────────────────

@Composable
private fun GlassSection(
    icon: ImageVector,
    title: String,
    description: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShape)
            .background(GlassBg)
            .border(0.5.dp, GlassBorder, GlassShape)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = PrimaryViolet,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = TextStyle(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PrimaryViolet,
                ),
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            description,
            style = TextStyle(fontSize = 12.sp, color = TextMuted, lineHeight = 16.sp),
            modifier = Modifier.padding(start = 32.dp),
        )
        Spacer(Modifier.height(16.dp))
        content()
    }
}

// ─── Debounced API Key Field ─────────────────────────────────────────────────

@Composable
private fun DebouncedApiKeyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: (String) -> Unit,
    hint: String,
    getKeyUrl: String,
    validateFormat: (String) -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    val isValid = value.isBlank() || validateFormat(value)

    Column {
        // Label
        Text(
            label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
            ),
        )
        Spacer(Modifier.height(6.dp))

        // Glass input field
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(InnerShape)
                .background(InputBg)
                .border(
                    width = 0.5.dp,
                    color = when {
                        value.isNotBlank() && !isValid -> ErrorRed.copy(alpha = 0.5f)
                        value.isNotBlank() && isValid -> SuccessGreen.copy(alpha = 0.3f)
                        else -> GlassBorder
                    },
                    shape = InnerShape,
                )
                .padding(horizontal = 14.dp, vertical = 4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { newValue ->
                        onValueChange(newValue)
                        saveJob?.cancel()
                        saveJob = scope.launch {
                            delay(500)
                            onSave(newValue)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        color = TextPrimary,
                    ),
                    cursorBrush = SolidColor(PrimaryViolet),
                    visualTransformation = if (visible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty()) {
                            Text(
                                "Enter key...",
                                style = TextStyle(fontSize = 14.sp, color = TextMuted),
                            )
                        }
                        innerTextField()
                    },
                )

                // Validation icon with glow
                if (value.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .drawBehind {
                                val glowColor = if (isValid) SuccessGreen else ErrorRed
                                drawCircle(
                                    color = glowColor.copy(alpha = 0.25f),
                                    radius = size.minDimension * 0.7f,
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (isValid) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isValid) SuccessGreen else ErrorRed,
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                // Visibility toggle
                IconButton(
                    onClick = { visible = !visible },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (visible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (visible) "Hide" else "Show",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        // Hint text
        Text(
            hint,
            style = TextStyle(fontSize = 11.sp, color = TextMuted),
        )

        Spacer(Modifier.height(8.dp))

        // "Get Free API Key" gradient button
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(VioletPinkGradient)
                .clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getKeyUrl)))
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.OpenInNew,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Get Free API Key",
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    ),
                )
            }
        }
    }
}

// ─── Model Picker Dialog ─────────────────────────────────────────────────────

@Composable
private fun ModelPickerDialog(
    availableModels: List<Pair<com.openclaw.android.llm.LlmProvider, com.openclaw.android.llm.ModelInfo>>,
    selectedModelId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(GlassShape)
                .background(SurfaceCharcoal)
                .border(0.5.dp, GlassBorder, GlassShape)
                .padding(24.dp),
        ) {
            // Dialog header
            Text(
                "Select Model",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    brush = VioletCyanGradient,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Choose your preferred AI model",
                style = TextStyle(fontSize = 12.sp, color = TextMuted),
            )
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Auto option
                val isAutoSelected = selectedModelId.isBlank()
                ModelOptionCard(
                    isSelected = isAutoSelected,
                    onClick = { onSelect("") },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isAutoSelected) PrimaryViolet.copy(alpha = 0.18f)
                                    else Color.White.copy(alpha = 0.04f),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            SparkleIcon(
                                tint = if (isAutoSelected) PrimaryViolet else TextMuted,
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Auto (best available)",
                                style = TextStyle(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAutoSelected) TextPrimary else TextSecondary,
                                ),
                            )
                            Text(
                                "Automatically picks the optimal model",
                                style = TextStyle(fontSize = 11.sp, color = TextMuted),
                            )
                        }
                        if (isAutoSelected) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PrimaryViolet,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // Provider models
                for ((provider, model) in availableModels) {
                    val isSelected = selectedModelId == model.id
                    ModelOptionCard(
                        isSelected = isSelected,
                        onClick = { onSelect(model.id) },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isSelected) PrimaryViolet.copy(alpha = 0.18f)
                                        else Color.White.copy(alpha = 0.04f),
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.SmartToy,
                                    contentDescription = null,
                                    tint = if (isSelected) PrimaryViolet else TextMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    model.displayName,
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) TextPrimary else TextSecondary,
                                    ),
                                )
                                Spacer(Modifier.height(3.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        provider.displayName,
                                        style = TextStyle(fontSize = 11.sp, color = TextMuted),
                                    )
                                    if (model.supportsVision) {
                                        MiniPill("Vision", SecondaryCyan)
                                    }
                                    Text(
                                        "${model.contextWindow / 1000}K ctx",
                                        style = TextStyle(fontSize = 11.sp, color = TextMuted),
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = PrimaryViolet,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Close button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InnerShape)
                    .background(GlassBg)
                    .border(0.5.dp, GlassBorder, InnerShape)
                    .clickable { onDismiss() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Close",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ModelOptionCard(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val borderBrush = if (isSelected) VioletCyanGradient
    else Brush.linearGradient(listOf(GlassBorder, GlassBorder))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .then(
                if (isSelected) Modifier.drawBehind {
                    drawRoundRect(
                        color = PrimaryViolet.copy(alpha = 0.06f),
                        size = size,
                    )
                } else Modifier
            )
            .background(if (isSelected) PrimaryViolet.copy(alpha = 0.05f) else InputBg)
            .border(width = if (isSelected) 1.dp else 0.5.dp, brush = borderBrush, shape = InnerShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        content()
    }
}

// ─── Space Card ──────────────────────────────────────────────────────────────

@Composable
private fun SpaceCard(
    space: Space,
    isActive: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    val borderBrush = if (isActive) VioletCyanGradient
    else Brush.linearGradient(listOf(GlassBorder, GlassBorder))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .then(
                if (isActive) Modifier.drawBehind {
                    // Glow effect behind the card
                    drawRoundRect(
                        color = PrimaryViolet.copy(alpha = 0.08f),
                        size = size,
                    )
                } else Modifier
            )
            .background(if (isActive) PrimaryViolet.copy(alpha = 0.06f) else InputBg)
            .border(
                width = if (isActive) 1.dp else 0.5.dp,
                brush = borderBrush,
                shape = InnerShape,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Emoji circle
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (isActive) PrimaryViolet.copy(alpha = 0.15f)
                        else Color.White.copy(alpha = 0.04f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    space.emoji,
                    style = TextStyle(fontSize = 20.sp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    space.name,
                    style = TextStyle(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    ),
                )
                if (space.description.isNotBlank()) {
                    Text(
                        space.description,
                        style = TextStyle(fontSize = 12.sp, color = TextMuted),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .drawBehind {
                            drawCircle(
                                color = PrimaryViolet.copy(alpha = 0.3f),
                                radius = size.minDimension * 0.7f,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Active",
                        tint = PrimaryViolet,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            if (onDelete != null) {
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(ErrorRed.copy(alpha = 0.08f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = ErrorRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ─── Create Space Dialog ─────────────────────────────────────────────────────

@Composable
private fun CreateSpaceDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String, description: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("\uD83D\uDCC1") }
    var description by remember { mutableStateOf("") }
    val emojiOptions = listOf(
        "\uD83D\uDCC1", "\uD83D\uDCBC", "\uD83D\uDCDD", "\uD83C\uDFA8",
        "\uD83D\uDD2C", "\uD83D\uDCCA", "\uD83C\uDFB5", "\uD83D\uDCF8",
        "\uD83C\uDFE0", "\u2708\uFE0F", "\uD83C\uDF73", "\uD83D\uDCAA",
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(GlassShape)
                .background(SurfaceCharcoal)
                .border(0.5.dp, GlassBorder, GlassShape)
                .padding(24.dp),
        ) {
            // Header
            Text(
                "New Space",
                style = TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    brush = VioletCyanGradient,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Create a workspace for your project",
                style = TextStyle(fontSize = 12.sp, color = TextMuted),
            )
            Spacer(Modifier.height(20.dp))

            // Emoji picker label
            Text(
                "Icon",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                ),
            )
            Spacer(Modifier.height(8.dp))

            // Emoji grid - row 1
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (e in emojiOptions.take(6)) {
                    val isSelected = emoji == e
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) PrimaryViolet.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.04f),
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) PrimaryViolet.copy(alpha = 0.5f) else GlassBorder,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, style = TextStyle(fontSize = 18.sp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            // Emoji grid - row 2
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (e in emojiOptions.drop(6)) {
                    val isSelected = emoji == e
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) PrimaryViolet.copy(alpha = 0.15f)
                                else Color.White.copy(alpha = 0.04f),
                            )
                            .border(
                                width = if (isSelected) 1.dp else 0.5.dp,
                                color = if (isSelected) PrimaryViolet.copy(alpha = 0.5f) else GlassBorder,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { emoji = e },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(e, style = TextStyle(fontSize = 18.sp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Space name input
            Text(
                "Space Name",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InnerShape)
                    .background(InputBg)
                    .border(0.5.dp, GlassBorder, InnerShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary),
                    cursorBrush = SolidColor(PrimaryViolet),
                    decorationBox = { innerTextField ->
                        if (name.isEmpty()) {
                            Text(
                                "e.g. My Project",
                                style = TextStyle(fontSize = 14.sp, color = TextMuted),
                            )
                        }
                        innerTextField()
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            // Description input
            Text(
                "Description (optional)",
                style = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .clip(InnerShape)
                    .background(InputBg)
                    .border(0.5.dp, GlassBorder, InnerShape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontSize = 14.sp, color = TextPrimary, lineHeight = 20.sp),
                    cursorBrush = SolidColor(PrimaryViolet),
                    decorationBox = { innerTextField ->
                        if (description.isEmpty()) {
                            Text(
                                "What is this space for?",
                                style = TextStyle(fontSize = 14.sp, color = TextMuted),
                            )
                        }
                        innerTextField()
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Cancel
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(InnerShape)
                        .background(GlassBg)
                        .border(0.5.dp, GlassBorder, InnerShape)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Cancel",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                        ),
                    )
                }

                // Create
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(InnerShape)
                        .background(
                            if (name.isNotBlank()) VioletCyanGradient
                            else Brush.linearGradient(
                                listOf(TextMuted.copy(alpha = 0.3f), TextMuted.copy(alpha = 0.3f)),
                            ),
                        )
                        .then(
                            if (name.isNotBlank()) Modifier.clickable {
                                onCreate(name.trim(), emoji, description.trim())
                            } else Modifier
                        )
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Create",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (name.isNotBlank()) Color.White
                            else TextMuted,
                        ),
                    )
                }
            }
        }
    }
}

// ─── Utility Composables ─────────────────────────────────────────────────────

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String? = null,
    tint: Color = TextSecondary,
    size: Dp = 48.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassBg)
            .border(0.5.dp, GlassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun MiniPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), PillShape)
            .border(0.5.dp, color.copy(alpha = 0.2f), PillShape)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            text,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            ),
        )
    }
}

@Composable
private fun SparkleIcon(
    tint: Color = PrimaryViolet,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sparkle")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sparkle_rotation",
    )

    Icon(
        Icons.Default.AutoAwesome,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer { rotationZ = rotation },
    )
}

// ─── Local Models Section ────────────────────────────────────────────────────

private val WarningAmber = Color(0xFFFBBF24)

@Composable
private fun LocalModelsSection(
    downloadManager: ModelDownloadManager,
    settings: SettingsRepository,
) {
    val scope = rememberCoroutineScope()
    val downloadState by downloadManager.downloadState.collectAsState()
    var downloadedModels by remember { mutableStateOf(downloadManager.getDownloadedModels()) }
    var activeModelId by remember { mutableStateOf(settings.getActiveLocalModelId()) }
    var localEnabled by remember { mutableStateOf(settings.getLocalModelEnabled()) }
    var showModelBrowser by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<String?>(null) }

    // Refresh downloaded models when download completes
    LaunchedEffect(downloadState) {
        if (downloadState is DownloadState.Complete) {
            downloadedModels = downloadManager.getDownloadedModels()
            val completedId = (downloadState as DownloadState.Complete).modelId
            if (activeModelId.isBlank()) {
                activeModelId = completedId
                settings.setActiveLocalModelId(completedId)
            }
        }
    }

    GlassSection(
        icon = Icons.Outlined.PhoneAndroid,
        title = "On-Device Models",
        description = "Download AI models to run locally. No internet needed.",
        trailing = {
            // Enable/disable toggle
            Switch(
                checked = localEnabled,
                onCheckedChange = {
                    localEnabled = it
                    settings.setLocalModelEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PrimaryViolet,
                    checkedTrackColor = PrimaryViolet.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = SurfaceCharcoal,
                ),
                modifier = Modifier.height(24.dp),
            )
        },
    ) {
        if (!localEnabled) {
            Text(
                "Local AI is disabled. Enable to use on-device models.",
                style = TextStyle(fontSize = 13.sp, color = TextMuted),
            )
            return@GlassSection
        }

        // Warn if native inference engine is not available (stub build)
        if (!com.openclaw.android.llm.LlamaBridge.isRealBuild) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(InnerShape)
                    .background(WarningAmber.copy(alpha = 0.1f))
                    .border(0.5.dp, WarningAmber.copy(alpha = 0.3f), InnerShape)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "\u26A0\uFE0F",
                        style = TextStyle(fontSize = 14.sp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "On-device inference is not available in this build. " +
                            "Models can be downloaded but won't run locally. " +
                            "Use a cloud model instead, or install a build with llama.cpp support.",
                        style = TextStyle(fontSize = 12.sp, color = WarningAmber),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        // Show downloaded models
        if (downloadedModels.isNotEmpty()) {
            Text(
                "INSTALLED",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                ),
            )
            Spacer(Modifier.height(8.dp))

            for (downloaded in downloadedModels) {
                val isActive = downloaded.model.id == activeModelId
                InstalledModelCard(
                    model = downloaded.model,
                    size = downloaded.sizeDisplay,
                    isActive = isActive,
                    onSelect = {
                        activeModelId = downloaded.model.id
                        settings.setActiveLocalModelId(downloaded.model.id)
                    },
                    onDelete = { showDeleteConfirm = downloaded.model.id },
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        // Download progress
        val currentDownload = downloadState
        if (currentDownload is DownloadState.Downloading) {
            DownloadProgressCard(
                state = currentDownload,
                onCancel = { downloadManager.cancelDownload() },
            )
            Spacer(Modifier.height(8.dp))
        }

        if (currentDownload is DownloadState.Error) {
            ErrorCard(
                modelName = currentDownload.modelName,
                message = currentDownload.message,
                onDismiss = { downloadManager.resetState() },
                onRetry = {
                    downloadManager.resetState()
                    scope.launch { downloadManager.downloadModel(currentDownload.modelId) }
                },
            )
            Spacer(Modifier.height(8.dp))
        }

        // Browse more models button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(InnerShape)
                .background(InputBg)
                .border(0.5.dp, GlassBorder, InnerShape)
                .clickable { showModelBrowser = true }
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = SecondaryCyan,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Browse Models to Download",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SecondaryCyan,
                    ),
                )
            }
        }

        // Disk usage
        val diskUsage = downloadManager.getTotalDiskUsage()
        if (diskUsage > 0) {
            Spacer(Modifier.height(8.dp))
            val gb = diskUsage / 1_000_000_000.0
            val display = if (gb >= 1.0) "%.1f GB".format(gb) else "${diskUsage / 1_000_000} MB"
            Text(
                "Total disk usage: $display",
                style = TextStyle(fontSize = 11.sp, color = TextMuted),
            )
        }

        // Available RAM info
        val availableRam = com.openclaw.android.llm.LlamaBridge.getAvailableMemoryMb()
        if (availableRam > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Available RAM: ${availableRam / 1024.0}GB",
                style = TextStyle(
                    fontSize = 11.sp,
                    color = if (availableRam > 3000) SuccessGreen else WarningAmber,
                ),
            )
        }
    }

    // Model browser dialog
    if (showModelBrowser) {
        ModelBrowserDialog(
            downloadManager = downloadManager,
            downloadedIds = downloadedModels.map { it.model.id }.toSet(),
            downloading = (downloadState as? DownloadState.Downloading)?.modelId,
            onDownload = { modelId ->
                scope.launch { downloadManager.downloadModel(modelId) }
            },
            onDismiss = { showModelBrowser = false },
        )
    }

    // Delete confirmation
    showDeleteConfirm?.let { modelId ->
        val model = downloadManager.availableModels.find { it.id == modelId }
        if (model != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = null },
                title = { Text("Delete ${model.name}?", color = TextPrimary) },
                text = {
                    Text(
                        "This will free ${model.sizeDisplay} of storage. You can re-download it later.",
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        downloadManager.deleteModel(modelId)
                        downloadedModels = downloadManager.getDownloadedModels()
                        if (activeModelId == modelId) {
                            activeModelId = downloadedModels.firstOrNull()?.model?.id ?: ""
                            settings.setActiveLocalModelId(activeModelId)
                        }
                        showDeleteConfirm = null
                    }) {
                        Text("Delete", color = ErrorRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = SurfaceCharcoal,
            )
        }
    }
}

@Composable
private fun InstalledModelCard(
    model: DownloadableModel,
    size: String,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(InputBg)
            .border(
                width = if (isActive) 1.dp else 0.5.dp,
                brush = if (isActive) VioletCyanGradient
                else Brush.linearGradient(listOf(GlassBorder, GlassBorder)),
                shape = InnerShape,
            )
            .clickable(onClick = onSelect)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        if (isActive) SuccessGreen else TextMuted.copy(alpha = 0.3f),
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    ),
                )
                Spacer(Modifier.height(2.dp))
                Row {
                    MiniPill(size, TextMuted)
                    Spacer(Modifier.width(4.dp))
                    MiniPill(model.tier.label, when (model.tier) {
                        ModelTier.SMALL -> SecondaryCyan
                        ModelTier.MEDIUM -> PrimaryViolet
                        ModelTier.LARGE -> TertiaryPink
                    })
                    if (model.supportsToolUse) {
                        Spacer(Modifier.width(4.dp))
                        MiniPill("Tools", SuccessGreen)
                    }
                }
            }
            if (isActive) {
                MiniPill("Active", SuccessGreen)
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete model",
                tint = TextMuted,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onDelete),
            )
        }
    }
}

@Composable
private fun DownloadProgressCard(
    state: DownloadState.Downloading,
    onCancel: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(InputBg)
            .border(0.5.dp, SecondaryCyan.copy(alpha = 0.3f), InnerShape)
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Downloading ${state.modelName}",
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) {
                    Text("Cancel", color = ErrorRed, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(PillShape),
                color = SecondaryCyan,
                trackColor = SurfaceCharcoal,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${state.progressPercent}% - ${state.downloadedDisplay} / ${state.totalDisplay}",
                style = TextStyle(fontSize = 12.sp, color = TextMuted),
            )
        }
    }
}

@Composable
private fun ErrorCard(
    modelName: String,
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(ErrorRed.copy(alpha = 0.08f))
            .border(0.5.dp, ErrorRed.copy(alpha = 0.3f), InnerShape)
            .padding(14.dp),
    ) {
        Column {
            Text(
                "Failed to download $modelName",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ErrorRed),
            )
            Spacer(Modifier.height(4.dp))
            Text(message, style = TextStyle(fontSize = 12.sp, color = TextSecondary))
            Spacer(Modifier.height(8.dp))
            Row {
                TextButton(onClick = onRetry) {
                    Text("Retry", color = SecondaryCyan, fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = TextMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ModelBrowserDialog(
    downloadManager: ModelDownloadManager,
    downloadedIds: Set<String>,
    downloading: String?,
    onDownload: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(GlassShape)
                .background(SurfaceCharcoal)
                .border(0.5.dp, GlassBorder, GlassShape)
                .padding(20.dp),
        ) {
            Column {
                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Download Model",
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            brush = VioletCyanGradient,
                        ),
                    )
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(onClick = onDismiss, icon = Icons.Default.Close)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose a model based on your device's RAM and storage.",
                    style = TextStyle(fontSize = 13.sp, color = TextSecondary),
                )
                Spacer(Modifier.height(16.dp))

                // Model list grouped by tier
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (tier in ModelTier.entries) {
                        val modelsInTier = downloadManager.availableModels.filter { it.tier == tier }
                        if (modelsInTier.isEmpty()) continue

                        val tierColor = when (tier) {
                            ModelTier.SMALL -> SecondaryCyan
                            ModelTier.MEDIUM -> PrimaryViolet
                            ModelTier.LARGE -> TertiaryPink
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(tierColor, CircleShape),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                tier.label.uppercase(),
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = tierColor,
                                    letterSpacing = 1.sp,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (tier) {
                                    ModelTier.SMALL -> "Works on any modern phone"
                                    ModelTier.MEDIUM -> "Recommended for flagship phones"
                                    ModelTier.LARGE -> "For 12GB+ RAM devices"
                                },
                                style = TextStyle(fontSize = 11.sp, color = TextMuted),
                            )
                        }
                        Spacer(Modifier.height(8.dp))

                        for (model in modelsInTier) {
                            val isDownloaded = model.id in downloadedIds
                            val isDownloading = model.id == downloading

                            DownloadableModelCard(
                                model = model,
                                isDownloaded = isDownloaded,
                                isDownloading = isDownloading,
                                onDownload = { onDownload(model.id) },
                            )
                            Spacer(Modifier.height(8.dp))
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadableModelCard(
    model: DownloadableModel,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onDownload: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(InnerShape)
            .background(InputBg)
            .border(
                0.5.dp,
                if (isDownloaded) SuccessGreen.copy(alpha = 0.3f) else GlassBorder,
                InnerShape,
            )
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.name,
                        style = TextStyle(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        model.description,
                        style = TextStyle(fontSize = 12.sp, color = TextSecondary),
                    )
                }
                Spacer(Modifier.width(12.dp))
                when {
                    isDownloaded -> {
                        MiniPill("Installed", SuccessGreen)
                    }
                    isDownloading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = SecondaryCyan,
                        )
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .background(SecondaryCyan.copy(alpha = 0.12f))
                                .border(0.5.dp, SecondaryCyan.copy(alpha = 0.3f), PillShape)
                                .clickable(onClick = onDownload)
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                        ) {
                            Text(
                                "Download",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SecondaryCyan,
                                ),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                MiniPill(model.sizeDisplay, TextMuted)
                Spacer(Modifier.width(4.dp))
                MiniPill("RAM: ${model.ramRequired}", TextMuted)
                Spacer(Modifier.width(4.dp))
                MiniPill("${model.contextWindow / 1000}K ctx", TextMuted)
                if (model.supportsToolUse) {
                    Spacer(Modifier.width(4.dp))
                    MiniPill("Tool Use", SuccessGreen)
                }
            }
        }
    }
}
