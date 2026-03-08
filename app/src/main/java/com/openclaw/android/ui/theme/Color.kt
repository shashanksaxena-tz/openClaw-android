package com.openclaw.android.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// OpenClaw Design System — Clean Minimal
// Light-first, white backgrounds, subtle grays, system-native feel
// ============================================================================

// ── Core Brand Color ────────────────────────────────────────────────────────

/** Soft teal — primary accent, subtle and professional */
val BrandTeal = Color(0xFF2AC4A0)
val BrandTealLight = Color(0xFF5EDFC4)
val BrandTealDim = Color(0xFF1A9E7F)
val BrandTealSubtle = Color(0xFFE8FAF5)

// ── Legacy aliases (referenced by other files) ──────────────────────────────
val ElectricViolet = Color(0xFF8E8E93)       // mapped to system gray
val ElectricVioletLight = Color(0xFFAEAEB2)
val ElectricVioletDim = Color(0xFF636366)
val NeonCyan = Color(0xFF2AC4A0)             // mapped to brand teal
val NeonCyanLight = Color(0xFF5EDFC4)
val NeonCyanDim = Color(0xFF1A9E7F)
val HotPink = Color(0xFFFF3B30)              // mapped to error red
val HotPinkLight = Color(0xFFFF6961)
val HotPinkDim = Color(0xFFD63028)

// ── Neutral Palette ─────────────────────────────────────────────────────────

val Neutral50 = Color(0xFFFAFAFA)
val Neutral100 = Color(0xFFF5F5F5)
val Neutral200 = Color(0xFFEEEEEE)
val Neutral300 = Color(0xFFE0E0E0)
val Neutral400 = Color(0xFFBDBDBD)
val Neutral500 = Color(0xFF9E9E9E)
val Neutral600 = Color(0xFF757575)
val Neutral700 = Color(0xFF616161)
val Neutral800 = Color(0xFF424242)
val Neutral900 = Color(0xFF212121)

// ── Semantic Colors ─────────────────────────────────────────────────────────

val SuccessGreen = Color(0xFF34C759)
val ErrorRedColor = Color(0xFFFF3B30)
val WarningAmber = Color(0xFFFF9500)
val InfoBlue = Color(0xFF007AFF)

// Legacy aliases
val BrightEmerald = SuccessGreen
val BrightEmeraldLight = Color(0xFF6EE7B7)
val BrightEmeraldDim = Color(0xFF059669)
val NeonRed = ErrorRedColor
val NeonRedLight = Color(0xFFFF6961)
val NeonRedDim = ErrorRedColor
val NeonRedContainer = Color(0xFFFDEDED)
val NeonAmber = WarningAmber
val NeonAmberDim = Color(0xFFF59E0B)

// Legacy glow aliases (now just neutral/brand)
val AiGlow = Color(0xFF8E8E93)
val AiGlowCyan = BrandTeal
val AiGlowPink = ErrorRedColor
val ShimmerStart = Color(0xFFF5F5F5)
val ShimmerMid = Color(0xFFE5E5EA)
val ShimmerEnd = Color(0xFFF5F5F5)

// ── Light Theme Palette ─────────────────────────────────────────────────────

val LightBackground = Color(0xFFFFFFFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFF2F2F7)
val LightSurfaceElevated = Color(0xFFF9F9F9)
val LightSurfaceBright = Color(0xFFE5E5EA)

val LightOnBackground = Color(0xFF000000)
val LightOnSurface = Color(0xFF000000)
val LightOnSurfaceVariant = Color(0xFF8E8E93)
val LightOnSurfaceDim = Color(0xFFC7C7CC)

val LightOutline = Color(0xFFD1D1D6)
val LightOutlineVariant = Color(0xFFE5E5EA)

val LightPrimary = Color(0xFF1C1C1E)
val LightOnPrimary = Color.White
val LightPrimaryContainer = Color(0xFFF2F2F7)
val LightOnPrimaryContainer = Color(0xFF1C1C1E)

val LightSecondary = Color(0xFF8E8E93)
val LightOnSecondary = Color.White
val LightSecondaryContainer = Color(0xFFF2F2F7)
val LightOnSecondaryContainer = Color(0xFF3C3C43)

val LightTertiary = BrandTeal
val LightOnTertiary = Color.White
val LightTertiaryContainer = BrandTealSubtle
val LightOnTertiaryContainer = BrandTealDim

val LightError = ErrorRedColor
val LightOnError = Color.White
val LightErrorContainer = Color(0xFFFDEDED)
val LightOnErrorContainer = Color(0xFF8B1A10)

// ── Dark Theme Palette ──────────────────────────────────────────────────────

val DarkBackground = Color(0xFF000000)
val DarkSurface = Color(0xFF1C1C1E)
val DarkSurfaceVariant = Color(0xFF2C2C2E)
val DarkSurfaceElevated = Color(0xFF3A3A3C)
val DarkSurfaceBright = Color(0xFF48484A)

val DarkOnBackground = Color(0xFFFFFFFF)
val DarkOnSurface = Color(0xFFFFFFFF)
val DarkOnSurfaceVariant = Color(0xFF8E8E93)
val DarkOnSurfaceDim = Color(0xFF636366)

val DarkOutline = Color(0xFF48484A)
val DarkOutlineVariant = Color(0xFF38383A)

val DarkPrimary = Color(0xFFFFFFFF)
val DarkOnPrimary = Color(0xFF1C1C1E)
val DarkPrimaryContainer = Color(0xFF2C2C2E)
val DarkOnPrimaryContainer = Color(0xFFFFFFFF)

val DarkSecondary = Color(0xFF8E8E93)
val DarkOnSecondary = Color.White
val DarkSecondaryContainer = Color(0xFF2C2C2E)
val DarkOnSecondaryContainer = Color(0xFFEBEBF5)

val DarkTertiary = BrandTealLight
val DarkOnTertiary = Color(0xFF003829)
val DarkTertiaryContainer = Color(0xFF004D3A)
val DarkOnTertiaryContainer = BrandTealLight

val DarkError = Color(0xFFFF6961)
val DarkOnError = Color(0xFF3B0907)
val DarkErrorContainer = Color(0xFF3B1714)
val DarkOnErrorContainer = Color(0xFFFFB4AB)

// ── Chat-Specific Colors ────────────────────────────────────────────────────

val UserBubbleGradientStart = Color(0xFFE5E5EA)
val UserBubbleGradientEnd = Color(0xFFE5E5EA)
val UserBubbleOnContent = Color(0xFF000000)

val AssistantBubbleDark = Color(0xFF1C1C1E)
val AssistantBubbleBorder = Color(0xFF38383A)
val AssistantBubbleLight = Color(0xFFFFFFFF)
val AssistantBubbleLightBorder = Color(0xFFE5E5EA)

val ToolBadgeColor = Color(0xFF8E8E93)
val ToolBadgeBackground = Color(0xFFF2F2F7)

val StreamingCursorColor = Color(0xFF1C1C1E)

// ── Gradient Presets ────────────────────────────────────────────────────────

val BrandGradientStart = BrandTeal
val BrandGradientMid = Color(0xFF80E0CC)
val BrandGradientEnd = Color(0xFFE8FAF5)

val GlassGradientStart = Color(0xFFF2F2F7)
val GlassGradientEnd = Color(0xFFFFFFFF)

val SuccessGradientStart = SuccessGreen
val SuccessGradientEnd = BrandTeal

val WelcomeGradientStart = Color(0xFFB2F5EA)
val WelcomeGradientEnd = Color(0xFFE8FAF5)
