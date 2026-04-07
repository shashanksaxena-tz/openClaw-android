package com.openclaw.android.ui.navigation

/**
 * Sealed class representing the 5 top-level screens reachable from MainContent.
 *
 * Replaces the previous `var currentTab: Int` (0-16) approach.
 * Each variant carries exactly the data it needs for rendering.
 */
sealed class MainScreen {

    /** Primary chat interface. Default screen on launch. */
    object ChatScreen : MainScreen()

    /** App-wide settings (theme, model, system prompt, etc.). */
    object SettingsScreen : MainScreen()

    /** Full-screen voice conversation mode. */
    object VoiceConversationScreen : MainScreen()

    /** Sandboxed file browser for the active space. */
    object FileBrowserScreen : MainScreen()

    /** Persistent memory / knowledge store viewer. */
    object MemoryScreen : MainScreen()
}
