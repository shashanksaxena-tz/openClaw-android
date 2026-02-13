package com.openclaw.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.openclaw.android.data.db.ConversationEntity
import com.openclaw.android.ui.screens.*
import com.openclaw.android.ui.theme.OpenClawTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as OpenClawApp

        setContent {
            OpenClawTheme {
                MainApp(app)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(app: OpenClawApp) {
    val scope = rememberCoroutineScope()

    // Check onboarding
    var showOnboarding by remember {
        mutableStateOf(!app.settings.hasAnyApiKey() && !app.settings.getOnboardingComplete())
    }

    if (showOnboarding) {
        OnboardingScreen(
            settings = app.settings,
            onComplete = {
                app.settings.setOnboardingComplete(true)
                showOnboarding = false
            },
        )
        return
    }

    // Redirect to settings if no API key after onboarding
    var currentTab by remember { mutableIntStateOf(if (app.settings.hasAnyApiKey()) 0 else 2) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val conversations by app.conversationManager.allConversations.collectAsState(initial = emptyList())

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ConversationHistoryPanel(
                    conversations = conversations,
                    activeConversationId = app.conversationManager.activeConversationId,
                    onNewConversation = {
                        scope.launch {
                            app.agentRuntime.startNewConversation()
                            drawerState.close()
                            currentTab = 0
                        }
                    },
                    onSelectConversation = { id ->
                        scope.launch {
                            app.agentRuntime.loadConversation(id)
                            drawerState.close()
                            currentTab = 0
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
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = NavigationBarDefaults.Elevation,
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Chat, "Chat") },
                        label = { Text("Chat") },
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Folder, "Files") },
                        label = { Text("Files") },
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Settings, "Settings") },
                        label = { Text("Settings") },
                    )
                }
            },
        ) { padding ->
            Crossfade(targetState = currentTab, label = "tab") { tab ->
                when (tab) {
                    0 -> ChatScreen(
                        runtime = app.agentRuntime,
                        onNavigateToSettings = { currentTab = 2 },
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
                    1 -> FileBrowserScreen(
                        fs = app.sandboxedFileSystem,
                        activeSpaceName = app.agentRuntime.activeSpaceName,
                        onAskAi = { file ->
                            scope.launch {
                                val content = try {
                                    if (file.length() < 100_000 && file.isFile) {
                                        file.readText()
                                    } else if (file.isFile) {
                                        file.readText().take(5000) + "\n... (truncated, file is ${file.length() / 1024}KB)"
                                    } else null
                                } catch (_: Exception) { null }

                                val text = if (content != null) {
                                    "Here's the file **${file.name}**:\n\n```\n$content\n```\n\nWhat can you tell me about this file?"
                                } else {
                                    "I have a file called ${file.name} (${file.length() / 1024}KB). What would you like to know about it?"
                                }
                                app.agentRuntime.sendMessage(text)
                                currentTab = 0
                            }
                        },
                        modifier = Modifier.padding(padding),
                    )
                    2 -> SettingsScreen(
                        settings = app.settings,
                        modelRouter = app.modelRouter,
                        spaceManager = app.spaceManager,
                        agentRuntime = app.agentRuntime,
                        onBack = { currentTab = 0 },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}
