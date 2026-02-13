package com.openclaw.android.ui.theme

import androidx.compose.ui.graphics.Color

// Modern AI palette — clean, professional, dark-mode-first

// Primary: Indigo/Violet gradient
val ModernPrimary = Color(0xFF6366F1)        // Indigo-500
val ModernPrimaryDark = Color(0xFF818CF8)    // Indigo-400 (lighter for dark theme)
val ModernPrimaryLight = Color(0xFFEEF2FF)   // Indigo-50

// Accent: Cyan/Teal for highlights
val AccentCyan = Color(0xFF06B6D4)           // Cyan-500
val AccentEmerald = Color(0xFF10B981)        // Emerald-500

// Light theme
val LightPrimary = ModernPrimary
val LightOnPrimary = Color.White
val LightPrimaryContainer = Color(0xFFE0E7FF)  // Indigo-100
val LightSecondary = AccentCyan
val LightBackground = Color(0xFFFAFAFC)
val LightSurface = Color.White
val LightSurfaceVariant = Color(0xFFF1F5F9)    // Slate-100
val LightOnSurface = Color(0xFF0F172A)         // Slate-900
val LightOnSurfaceVariant = Color(0xFF64748B)  // Slate-500
val LightOutline = Color(0xFFE2E8F0)           // Slate-200
val LightError = Color(0xFFEF4444)             // Red-500

// Dark theme
val DarkPrimary = ModernPrimaryDark
val DarkOnPrimary = Color(0xFF1E1B4B)          // Indigo-950
val DarkPrimaryContainer = Color(0xFF312E81)   // Indigo-800
val DarkSecondary = Color(0xFF22D3EE)          // Cyan-400
val DarkBackground = Color(0xFF0F0F14)         // Near black
val DarkSurface = Color(0xFF1A1A24)            // Dark surface
val DarkSurfaceVariant = Color(0xFF252530)     // Slightly lighter
val DarkOnSurface = Color(0xFFF1F5F9)          // Slate-100
val DarkOnSurfaceVariant = Color(0xFF94A3B8)   // Slate-400
val DarkOutline = Color(0xFF334155)            // Slate-700
val DarkError = Color(0xFFF87171)              // Red-400

// Chat-specific
val UserBubbleLight = ModernPrimary
val UserBubbleDark = ModernPrimaryDark
val AssistantBubbleLight = Color(0xFFF1F5F9)   // Slate-100
val AssistantBubbleDark = Color(0xFF1E1E2E)    // Dark bubble
val ToolBadgeColor = Color(0xFF8B5CF6)         // Violet-500
val SuccessColor = AccentEmerald
