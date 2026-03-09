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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.data.SettingsRepository
import com.openclaw.android.ui.screens.*
import com.openclaw.android.ui.theme.OpenClawTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── State machine ──────────────────────────────────────────────────────────────
private enum class AppScreen { SPLASH, ONBOARDING, MAIN }

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
            // Resolve theme preference: light (default), dark, or follow system
            val themeMode = remember { mutableStateOf(app.settings.getThemeMode()) }
            val isDarkTheme = when (themeMode.value) {
                SettingsRepository.THEME_DARK -> true
                SettingsRepository.THEME_SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                else -> false // THEME_LIGHT is default
            }
            OpenClawTheme(darkTheme = isDarkTheme) {
                MainApp(app, themeMode)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Root composable — state machine
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(app: OpenClawApp, themeMode: MutableState<String> = mutableStateOf(SettingsRepository.THEME_LIGHT)) {
    var currentScreen by remember { mutableStateOf(AppScreen.SPLASH) }

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
            fadeIn(animationSpec = tween(400)) togetherWith
                    fadeOut(animationSpec = tween(300))
        },
    ) { screen ->
        when (screen) {
            AppScreen.SPLASH -> {
                SplashScreen(
                    onFinished = {
                        val needsOnboarding =
                            !app.settings.hasAnyProvider() && !app.settings.getOnboardingComplete()
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
                MainContent(app, themeMode)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Splash screen — clean, minimal
// ════════════════════════════════════════════════════════════════════════════════

@Composable
private fun SplashScreen(onFinished: () -> Unit) {
    val logoAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(600, easing = EaseOutCubic),
        label = "logo-alpha",
    )

    LaunchedEffect(Unit) {
        delay(1500)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = logoAlpha },
        ) {
            Text(
                text = "OpenClaw",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "AI on your terms",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  Main content — chat-first, fullscreen with drawer
// ════════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainContent(app: OpenClawApp, themeMode: MutableState<String> = mutableStateOf(SettingsRepository.THEME_LIGHT)) {
    val scope = rememberCoroutineScope()

    // currentTab: 0=Dashboard, 1=Tasks, 2=Notes, 3=Chat, 4=Discover,
    // 5=Settings, 6=Calendar, 7=Travel, 8=Insights, 9=Briefing,
    // 10=Team, 11=Files, 12=Voice, 13=Habits, 14=Decisions, 15=Memory, 16=Reminders
    var currentTab by remember { mutableIntStateOf(3) } // Start on Chat
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val conversations by app.conversationManager.allConversations.collectAsState(initial = emptyList())

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp,
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
        // No bottom bar — fullscreen content
        AnimatedContent(
            targetState = currentTab,
            label = "tab-content",
            transitionSpec = {
                fadeIn(tween(250)) togetherWith fadeOut(tween(200))
            },
        ) { tab ->
            when (tab) {
                0 -> DashboardScreen(
                    modifier = Modifier,
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
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                )

                2 -> NotesScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                )

                3 -> ChatScreen(
                    runtime = app.agentRuntime,
                    onNavigateToSettings = { currentTab = 5 },
                    modifier = Modifier,
                    onOpenDrawer = { scope.launch { drawerState.open() } },
                    modelRouter = app.modelRouter,
                    onNavigateToDashboard = { currentTab = 0 },
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
                    modifier = Modifier,
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
                    onBack = { currentTab = 3 },
                    modifier = Modifier,
                    onNavigateToFiles = { currentTab = 11 },
                    themeMode = themeMode,
                )

                6 -> CalendarScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                )

                7 -> TravelScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                )

                8 -> InsightsScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                )

                9 -> BriefingScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                    onNavigateToTasks = { currentTab = 1 },
                    onNavigateToCalendar = { currentTab = 6 },
                    onBack = { currentTab = 3 },
                )

                10 -> TeamScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                )

                11 -> FileBrowserScreen(
                    fs = app.sandboxedFileSystem,
                    modifier = Modifier,
                    activeSpaceName = app.agentRuntime.activeSpaceName,
                )

                12 -> VoiceConversationScreen(
                    runtime = app.agentRuntime,
                    onDismiss = { currentTab = 3 },
                    modifier = Modifier,
                )

                13 -> HabitsScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                    onBack = { currentTab = 3 },
                )

                14 -> DecisionScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                    onBack = { currentTab = 3 },
                )

                15 -> MemoryScreen(
                    memorySystem = app.memorySystem,
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                )

                16 -> RemindersScreen(
                    modifier = Modifier,
                    onNavigateToChat = { currentTab = 3 },
                    onBack = { currentTab = 3 },
                )
            }
        }
    }
}
