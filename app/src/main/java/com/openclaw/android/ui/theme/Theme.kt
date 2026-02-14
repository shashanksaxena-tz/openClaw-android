package com.openclaw.android.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// ============================================================================
// OpenClaw Theme — Premium AI Experience
// Dark-first glass morphism with electric neon accents
// ============================================================================

private val OpenClawDarkColorScheme = darkColorScheme(
    // Primary — Electric Violet
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,

    // Secondary — Neon Cyan
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,

    // Tertiary — Hot Pink
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,

    // Backgrounds — true deep space blacks
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,

    // Surface tint and containers
    surfaceTint = ElectricViolet,
    surfaceContainerLowest = Color(0xFF030306),
    surfaceContainerLow = Color(0xFF0A0A0F),
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkSurfaceBright,

    // Outline
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,

    // Inverse
    inverseSurface = Color(0xFFE8E8F0),
    inverseOnSurface = Color(0xFF12121A),
    inversePrimary = ElectricViolet,

    // Error
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,

    // Scrim
    scrim = Color(0xFF000000),
)

private val OpenClawLightColorScheme = lightColorScheme(
    // Primary — Electric Violet
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,

    // Secondary — Neon Cyan (dimmed for readability)
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,

    // Tertiary — Hot Pink
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,

    // Backgrounds — clean white with violet undertone
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,

    // Surface tint and containers
    surfaceTint = ElectricViolet,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFBFF),
    surfaceContainer = Color(0xFFF8F7FE),
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightSurfaceBright,

    // Outline
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,

    // Inverse
    inverseSurface = Color(0xFF1A1A26),
    inverseOnSurface = Color(0xFFF0F0F8),
    inversePrimary = ElectricVioletLight,

    // Error
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,

    // Scrim
    scrim = Color(0xFF000000),
)

// ── Shapes — pill-forward, generously rounded ───────────────────────────────

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),       // Chips, small badges
    small = RoundedCornerShape(14.dp),           // Input fields, small cards
    medium = RoundedCornerShape(20.dp),          // Cards, dialogs
    large = RoundedCornerShape(28.dp),           // Bottom sheets, large cards
    extraLarge = RoundedCornerShape(32.dp),      // Full pill shapes, FABs
)

// ── Theme Composable ────────────────────────────────────────────────────────

@Composable
fun OpenClawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) OpenClawDarkColorScheme else OpenClawLightColorScheme

    // Edge-to-edge: transparent status bar, surface-matching navigation bar
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as Activity
            val window = activity.window

            // Make status bar transparent for edge-to-edge
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()

            // Navigation bar matches surface for seamless feel
            @Suppress("DEPRECATION")
            window.navigationBarColor = colorScheme.surface.toArgb()

            // Set system bar icon contrast
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme

            // Enable edge-to-edge drawing
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
