package com.openclaw.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.ui.screens.*
import com.openclaw.android.ui.theme.OpenClawTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Design tokens ──────────────────────────────────────────────────────────────
private val TrueBlack = Color(0xFF050508)
private val ElectricViolet = Color(0xFFA855F7)
private val NeonCyan = Color(0xFF22D3EE)
private val GlassSurface = Color(0xFF0D0D12)
private val GlassBorder = Color(0xFF1F1F2E)
private val SubtleWhite = Color(0xB3F1F5F9)  // ~70 % white

// ── State machine ──────────────────────────────────────────────────────────────
private enum class AppScreen { SPLASH, ONBOARDING, MAIN }

// ── Tab definition ─────────────────────────────────────────────────────────────
private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("Home", Icons.Rounded.Home),
    TabItem("Tasks", Icons.Rounded.CheckCircle),
    TabItem("Notes", Icons.Rounded.EditNote),
    TabItem("Chat", Icons.Rounded.Chat),
    TabItem("More", Icons.Rounded.Apps),
)

// ════════════════════════════════════════════════════════════════════════════════
//  Activity
// ════════════════════════════════════════════════════════════════════════════════

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OpenClawApp
        app.currentWindow = window

        setContent {
            OpenClawTheme {
                MainApp(app)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Root composable — state machine
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(app: OpenClawApp) {
    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }

    // ── Runtime permission bridge ────────────────────────────────────────────
    // Observes permission requests from tools (via PermissionManager) and shows
    // the system permission dialog. The result is sent back so the suspended
    // tool coroutine can continue.
    var pendingRequest by remember { mutableStateOf<PermissionManager.PermissionRequest?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val req = pendingRequest ?: return@rememberLauncherForActivityResult
        val allGranted = grants.values.all { it }
        app.permissionManager.onPermissionResult(req, allGranted)
        pendingRequest = null
    }

    LaunchedEffect(Unit) {
        app.permissionManager.requests.collect { request ->
            pendingRequest = request
            permissionLauncher.launch(request.permissions.toTypedArray())
        }
    }

    AnimatedContent(
        targetState = currentScreen,
        label = "screen-transition",
        transitionSpec = {
            fadeIn(animationSpec = tween(500)) togetherWith
                    fadeOut(animationSpec = tween(400))
        },
    ) { screen ->
        when (screen) {
            AppScreen.SPLASH -> {
                SplashScreen(
                    onFinished = {
                        val needsOnboarding =
                            !app.settings.hasAnyApiKey() && !app.settings.getOnboardingComplete()
                        currentScreen =
                            if (needsOnboarding) AppScreen.ONBOARDING else AppScreen.MAIN
                    },
                )
            }

            AppScreen.ONBOARDING -> {
                OnboardingScreen(
                    settings = app.settings,
                    onComplete = {
                        app.settings.setOnboardingComplete(true)
                        currentScreen = AppScreen.MAIN
                    },
                )
            }

            AppScreen.MAIN -> {
                MainContent(app)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Splash screen
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    // Animate logo entrance
    val infiniteTransition = rememberInfiniteTransition(label = "splash-glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-alpha",
    )

    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "logo-alpha",
    )

    val logoScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "logo-scale",
    )

    LaunchedEffect(Unit) {
        delay(2000)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack),
        contentAlignment = Alignment.Center,
    ) {
        // Radial glow behind logo
        Box(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer { alpha = glowAlpha }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            ElectricViolet.copy(alpha = 0.35f),
                            NeonCyan.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                    shape = RoundedCornerShape(50),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                alpha = logoAlpha
                scaleX = logoScale
                scaleY = logoScale
            },
        ) {
            // App icon placeholder — electric violet claw mark
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(ElectricViolet, NeonCyan),
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "{ }",
                    color = TrueBlack,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "OpenClaw",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "AI on your terms",
                color = NeonCyan.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Main content — drawer + scaffold + glass bottom nav
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(app: OpenClawApp) {
    val scope = rememberCoroutineScope()

    var currentTab by remember { mutableIntStateOf(0) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val conversations by app.conversationManager.allConversations.collectAsState(initial = emptyList())

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = GlassSurface.copy(alpha = 0.92f),
                drawerTonalElevation = 0.dp,
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                modifier = Modifier
                    .drawBehind {
                        // Subtle top-edge gradient border
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    ElectricViolet.copy(alpha = 0.25f),
                                    Color.Transparent,
                                ),
                                startY = 0f,
                                endY = size.height * 0.15f,
                            ),
                        )
                    },
            ) {
                ConversationHistoryPanel(
                    conversations = conversations,
                    activeConversationId = app.conversationManager.activeConversationId,
                    onNewConversation = {
                        scope.launch {
                            app.agentRuntime.startNewConversation()
                            drawerState.close()
                            currentTab = 3
                        }
                    },
                    onSelectConversation = { id ->
                        scope.launch {
                            app.agentRuntime.loadConversation(id)
                            drawerState.close()
                            currentTab = 3
                        }
                    },
                    onDeleteConversation = { id ->
                        scope.launch {
                            app.conversationManager.deleteConversation(id)
                        }
                    },
                    onRenameConversation = { id, newTitle ->
                        app.conversationManager.renameConversation(id, newTitle)
                    },
                    onSearchConversations = { query ->
                        app.conversationManager.searchConversations(query)
                    },
                )
            }
        },
    ) {
        Scaffold(
            containerColor = TrueBlack,
            bottomBar = {
                GlassNavigationBar(
                    currentTab = currentTab,
                    onTabSelected = { currentTab = it },
                )
            },
        ) { padding ->
            AnimatedContent(
                targetState = currentTab,
                label = "tab-content",
                transitionSpec = {
                    val direction = if (targetState > initialState) {
                        AnimatedContentTransitionScope.SlideDirection.Start
                    } else {
                        AnimatedContentTransitionScope.SlideDirection.End
                    }
                    slideIntoContainer(direction, tween(350, easing = EaseOutCubic)) + fadeIn(tween(250)) togetherWith
                            slideOutOfContainer(direction, tween(350, easing = EaseInCubic)) + fadeOut(tween(200))
                },
            ) { tab ->
                when (tab) {
                    0 -> DashboardScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                        onNavigateToTasks = { currentTab = 1 },
                        onNavigateToNotes = { currentTab = 2 },
                        onNavigateToTeam = { currentTab = 10 },
                        onNavigateToSettings = { currentTab = 5 },
                        onNavigateToCalendar = { currentTab = 6 },
                        onNavigateToTravel = { currentTab = 7 },
                        onNavigateToInsights = { currentTab = 8 },
                        onNavigateToBriefing = { currentTab = 9 },
                        onNavigateToHabits = { currentTab = 13 },
                        onNavigateToReminders = { currentTab = 16 },
                        onNavigateToMemory = { currentTab = 15 },
                        onNavigateToVoice = { currentTab = 12 },
                        onNavigateToFiles = { currentTab = 11 },
                    )

                    1 -> TasksScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                    )

                    2 -> NotesScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                    )

                    3 -> ChatScreen(
                        runtime = app.agentRuntime,
                        onNavigateToSettings = { currentTab = 5 },
                        modifier = Modifier.padding(padding),
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        modelRouter = app.modelRouter,
                        onExportChat = { filename ->
                            try {
                                val text = app.conversationManager.getConversationAsText()
                                if (text.isNotBlank()) {
                                    val file = app.sandboxedFileSystem.resolve(filename).getOrNull()
                                    if (file != null) {
                                        file.parentFile?.mkdirs()
                                        val content = buildString {
                                            appendLine("# Conversation Export")
                                            appendLine("_Exported on ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}_")
                                            appendLine()
                                            appendLine("---")
                                            appendLine()
                                            append(text)
                                        }
                                        file.writeText(content)
                                        true
                                    } else false
                                } else false
                            } catch (_: Exception) { false }
                        },
                    )

                    4 -> DiscoverScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                        onNavigateToTasks = { currentTab = 1 },
                        onNavigateToNotes = { currentTab = 2 },
                        onNavigateToTeam = { currentTab = 10 },
                        onNavigateToCalendar = { currentTab = 6 },
                        onNavigateToTravel = { currentTab = 7 },
                        onNavigateToInsights = { currentTab = 8 },
                        onNavigateToBriefing = { currentTab = 9 },
                        onNavigateToSettings = { currentTab = 5 },
                        onNavigateToFiles = { currentTab = 11 },
                        onNavigateToVoice = { currentTab = 12 },
                        onNavigateToHabits = { currentTab = 13 },
                        onNavigateToDecisions = { currentTab = 14 },
                        onNavigateToMemory = { currentTab = 15 },
                        onNavigateToReminders = { currentTab = 16 },
                    )

                    5 -> SettingsScreen(
                        settings = app.settings,
                        modelRouter = app.modelRouter,
                        spaceManager = app.spaceManager,
                        agentRuntime = app.agentRuntime,
                        conversationExpiry = app.conversationExpiry,
                        privacyAudit = app.privacyAudit,
                        onBack = { currentTab = 0 },
                        modifier = Modifier.padding(padding),
                        onNavigateToFiles = { currentTab = 11 },
                    )

                    6 -> CalendarScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                    )

                    7 -> TravelScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                    )

                    8 -> InsightsScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                    )

                    9 -> BriefingScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                        onNavigateToTasks = { currentTab = 1 },
                        onNavigateToCalendar = { currentTab = 6 },
                        onBack = { currentTab = 0 },
                    )

                    10 -> TeamScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                    )

                    11 -> FileBrowserScreen(
                        fs = app.sandboxedFileSystem,
                        modifier = Modifier.padding(padding),
                        activeSpaceName = app.agentRuntime.activeSpaceName,
                    )

                    12 -> VoiceConversationScreen(
                        runtime = app.agentRuntime,
                        onDismiss = { currentTab = 3 },
                        modifier = Modifier.padding(padding),
                    )

                    13 -> HabitsScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                        onBack = { currentTab = 0 },
                    )

                    14 -> DecisionScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                        onBack = { currentTab = 0 },
                    )

                    15 -> MemoryScreen(
                        memorySystem = app.memorySystem,
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                    )

                    16 -> RemindersScreen(
                        modifier = Modifier.padding(padding),
                        onNavigateToChat = { currentTab = 3 },
                        onBack = { currentTab = 0 },
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Glass-morphism bottom navigation bar
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun GlassNavigationBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val indicatorOffset by animateFloatAsState(
        targetValue = currentTab.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "indicator-offset",
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Top edge glow line
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            ElectricViolet.copy(alpha = 0.4f),
                            NeonCyan.copy(alpha = 0.3f),
                            Color.Transparent,
                        ),
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            .background(GlassSurface.copy(alpha = 0.80f))
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                GlassNavItem(
                    tab = tab,
                    isSelected = currentTab == index,
                    indicatorProgress = (1f - (indicatorOffset - index).coerceIn(-1f, 1f).let { kotlin.math.abs(it) }),
                    onClick = { onTabSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GlassNavItem(
    tab: TabItem,
    isSelected: Boolean,
    indicatorProgress: Float, // 0 = fully away, 1 = fully here
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.45f,
        animationSpec = tween(300),
        label = "icon-alpha",
    )

    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(250),
        label = "label-alpha",
    )

    val pillScale by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pill-scale",
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Pill background with glow
            if (pillScale > 0.01f) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(32.dp)
                        .graphicsLayer {
                            scaleX = pillScale
                            scaleY = pillScale
                            alpha = indicatorProgress
                        }
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = ElectricViolet.copy(alpha = 0.5f),
                            spotColor = ElectricViolet.copy(alpha = 0.5f),
                        )
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    ElectricViolet.copy(alpha = 0.85f),
                                    ElectricViolet.copy(alpha = 0.55f),
                                ),
                            ),
                            shape = RoundedCornerShape(16.dp),
                        ),
                )
            }

            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = if (isSelected) Color.White else SubtleWhite,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { alpha = iconAlpha },
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = tab.label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) NeonCyan else SubtleWhite,
            modifier = Modifier.graphicsLayer { alpha = labelAlpha },
        )
    }
}
