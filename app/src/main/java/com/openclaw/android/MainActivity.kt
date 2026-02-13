package com.openclaw.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.openclaw.android.ui.screens.ChatScreen
import com.openclaw.android.ui.screens.FileBrowserScreen
import com.openclaw.android.ui.screens.SettingsScreen
import com.openclaw.android.ui.screens.SpacesScreen
import com.openclaw.android.ui.theme.OpenClawTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OpenClawApp

        setContent {
            OpenClawTheme {
                MainNavigation(app)
            }
        }
    }
}

@Composable
private fun MainNavigation(app: OpenClawApp) {
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = NavigationBarDefaults.Elevation,
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat") },
                    label = { Text("Chat") },
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Workspaces, contentDescription = "Spaces") },
                    label = { Text("Spaces") },
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Files") },
                    label = { Text("Files") },
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Crossfade(targetState = currentTab, label = "tab_transition") { tab ->
            when (tab) {
                0 -> ChatScreen(
                    runtime = app.agentRuntime,
                    onNavigateToSettings = { currentTab = 3 },
                    modifier = Modifier.padding(padding),
                )
                1 -> SpacesScreen(
                    spaceManager = app.spaceManager,
                    agentRuntime = app.agentRuntime,
                    modifier = Modifier.padding(padding),
                )
                2 -> FileBrowserScreen(
                    fs = app.sandboxedFileSystem,
                    modifier = Modifier.padding(padding),
                )
                3 -> SettingsScreen(
                    settings = app.settings,
                    modelRouter = app.modelRouter,
                    onBack = { currentTab = 0 },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (!app.settings.hasAnyApiKey()) {
        LaunchedEffect(Unit) {
            currentTab = 3
        }
    }
}
