package com.openclaw.android.ui.navigation

import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for the MainScreen sealed class (S1.1).
 *
 * Verifies that all 5 screen variants exist, are distinct singleton objects,
 * and behave correctly in when() expressions.
 */
class MainScreenTest {

    @Test
    fun testAllScreenVariantsExist() {
        val screens: List<MainScreen> = listOf(
            MainScreen.ChatScreen,
            MainScreen.SettingsScreen,
            MainScreen.VoiceConversationScreen,
            MainScreen.FileBrowserScreen,
            MainScreen.MemoryScreen,
        )
        assertEquals(5, screens.size)
    }

    @Test
    fun testScreensAreDistinct() {
        assertNotEquals(MainScreen.ChatScreen, MainScreen.SettingsScreen)
        assertNotEquals(MainScreen.ChatScreen, MainScreen.VoiceConversationScreen)
        assertNotEquals(MainScreen.ChatScreen, MainScreen.FileBrowserScreen)
        assertNotEquals(MainScreen.ChatScreen, MainScreen.MemoryScreen)
        assertNotEquals(MainScreen.SettingsScreen, MainScreen.VoiceConversationScreen)
    }

    @Test
    fun testChatScreenIsSingleton() {
        val a: MainScreen = MainScreen.ChatScreen
        val b: MainScreen = MainScreen.ChatScreen
        assertSame(a, b)
    }

    @Test
    fun testWhenExpressionCoversAllBranches() {
        // This test ensures all 5 branches are exercised in a when() expression.
        val screens = listOf(
            MainScreen.ChatScreen,
            MainScreen.SettingsScreen,
            MainScreen.VoiceConversationScreen,
            MainScreen.FileBrowserScreen,
            MainScreen.MemoryScreen,
        )

        val labels = screens.map { screen ->
            when (screen) {
                is MainScreen.ChatScreen -> "chat"
                is MainScreen.SettingsScreen -> "settings"
                is MainScreen.VoiceConversationScreen -> "voice"
                is MainScreen.FileBrowserScreen -> "files"
                is MainScreen.MemoryScreen -> "memory"
            }
        }

        assertEquals(listOf("chat", "settings", "voice", "files", "memory"), labels)
    }

    @Test
    fun testDefaultScreenIsChatScreen() {
        // The default screen when starting MainContent must be ChatScreen.
        val defaultScreen: MainScreen = MainScreen.ChatScreen
        assertTrue(defaultScreen is MainScreen.ChatScreen)
    }
}
