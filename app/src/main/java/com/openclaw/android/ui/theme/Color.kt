package com.openclaw.android.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// OpenClaw Design System — "Created by AI" Aesthetic
// Dark-first, electric neon accents, glass morphism, vibrant gradients
// ============================================================================

// ── Core Brand Colors ───────────────────────────────────────────────────────

/** Electric Violet — the soul of OpenClaw */
val ElectricViolet = Color(0xFFA855F7)
val ElectricVioletLight = Color(0xFFC084FC)     // Lighter variant for hover/pressed
val ElectricVioletDim = Color(0xFF7C3AED)       // Deeper variant for containers
val ElectricVioletSubtle = Color(0xFF2E1065)    // Very dark violet for tinted surfaces

/** Neon Cyan — secondary accent, futuristic edge */
val NeonCyan = Color(0xFF22D3EE)
val NeonCyanLight = Color(0xFF67E8F9)
val NeonCyanDim = Color(0xFF0891B2)
val NeonCyanSubtle = Color(0xFF083344)

/** Hot Pink — tertiary accent, special moments */
val HotPink = Color(0xFFEC4899)
val HotPinkLight = Color(0xFFF472B6)
val HotPinkDim = Color(0xFFDB2777)

/** Bright Emerald — success states */
val BrightEmerald = Color(0xFF34D399)
val BrightEmeraldLight = Color(0xFF6EE7B7)
val BrightEmeraldDim = Color(0xFF059669)

/** Error / Danger */
val NeonRed = Color(0xFFFF6B6B)
val NeonRedLight = Color(0xFFFCA5A5)
val NeonRedDim = Color(0xFFEF4444)
val NeonRedContainer = Color(0xFF3B0F0F)

/** Warning */
val NeonAmber = Color(0xFFFBBF24)
val NeonAmberDim = Color(0xFFF59E0B)

// ── AI Glow & Animation Colors ──────────────────────────────────────────────

/** AI glow effect — used for pulsing halos, thinking indicators, shimmer */
val AiGlow = Color(0xFFA855F7)
val AiGlowCyan = Color(0xFF22D3EE)
val AiGlowPink = Color(0xFFEC4899)

/** Shimmer gradient stops for AI-powered loading states */
val ShimmerStart = Color(0xFF161622)
val ShimmerMid = Color(0xFF2A1F4E)
val ShimmerEnd = Color(0xFF161622)

// ── Dark Theme Palette ──────────────────────────────────────────────────────

// Backgrounds — true deep space blacks
val DarkBackground = Color(0xFF050508)          // True black, the void
val DarkSurface = Color(0xFF0D0D12)             // Charcoal, main surface
val DarkSurfaceVariant = Color(0xFF12121A)      // Deep navy, cards & containers
val DarkSurfaceElevated = Color(0xFF1A1A26)     // Elevated surfaces, modals
val DarkSurfaceBright = Color(0xFF22222F)       // Highest elevation

// On-colors for dark theme
val DarkOnBackground = Color(0xFFF0F0F8)        // Near-white with cool tint
val DarkOnSurface = Color(0xFFE8E8F0)           // Primary text
val DarkOnSurfaceVariant = Color(0xFF9898AC)    // Secondary text, muted
val DarkOnSurfaceDim = Color(0xFF5C5C72)        // Disabled / hint text

// Outline / borders
val DarkOutline = Color(0xFF2A2A3C)             // Subtle borders
val DarkOutlineVariant = Color(0xFF1E1E2C)      // Very subtle dividers

// Primary in dark theme — brighter violet for contrast
val DarkPrimary = ElectricVioletLight           // #C084FC
val DarkOnPrimary = Color(0xFF1A0530)           // Deep violet-black
val DarkPrimaryContainer = ElectricVioletDim    // #7C3AED
val DarkOnPrimaryContainer = Color(0xFFEDE9FE)  // Very light violet

// Secondary
val DarkSecondary = NeonCyan                    // #22D3EE
val DarkOnSecondary = Color(0xFF052E38)         // Deep cyan-black
val DarkSecondaryContainer = NeonCyanDim        // #0891B2
val DarkOnSecondaryContainer = Color(0xFFCFFAFE)

// Tertiary
val DarkTertiary = HotPink                      // #EC4899
val DarkOnTertiary = Color(0xFF380D24)
val DarkTertiaryContainer = HotPinkDim          // #DB2777
val DarkOnTertiaryContainer = Color(0xFFFCE7F3)

// Error
val DarkError = NeonRed
val DarkOnError = Color(0xFF2D0A0A)
val DarkErrorContainer = NeonRedContainer
val DarkOnErrorContainer = NeonRedLight

// ── Light Theme Palette ─────────────────────────────────────────────────────

// Backgrounds — clean white with cool violet undertone
val LightBackground = Color(0xFFFAFBFF)         // Warm white, violet-kissed
val LightSurface = Color(0xFFFFFFFF)            // Pure white
val LightSurfaceVariant = Color(0xFFF3F2FA)     // Soft violet tint
val LightSurfaceElevated = Color(0xFFF8F7FE)    // Slightly tinted
val LightSurfaceBright = Color(0xFFEDECF6)      // Visible violet wash

// On-colors for light theme
val LightOnBackground = Color(0xFF0C0C18)       // Near-black, cool
val LightOnSurface = Color(0xFF12121F)          // Primary text
val LightOnSurfaceVariant = Color(0xFF5C5972)   // Secondary text
val LightOnSurfaceDim = Color(0xFF9895AC)       // Hint text

// Outline / borders
val LightOutline = Color(0xFFD6D3E8)            // Soft violet border
val LightOutlineVariant = Color(0xFFE8E6F2)     // Very subtle

// Primary in light theme
val LightPrimary = ElectricViolet               // #A855F7
val LightOnPrimary = Color.White
val LightPrimaryContainer = Color(0xFFF3E8FF)   // Very light violet
val LightOnPrimaryContainer = Color(0xFF3B0764)

// Secondary
val LightSecondary = NeonCyanDim                // #0891B2, darker for readability
val LightOnSecondary = Color.White
val LightSecondaryContainer = Color(0xFFE0FCFF)
val LightOnSecondaryContainer = Color(0xFF052E38)

// Tertiary
val LightTertiary = HotPinkDim                  // #DB2777
val LightOnTertiary = Color.White
val LightTertiaryContainer = Color(0xFFFFF0F7)
val LightOnTertiaryContainer = Color(0xFF4A0D2B)

// Error
val LightError = NeonRedDim
val LightOnError = Color.White
val LightErrorContainer = Color(0xFFFEE2E2)
val LightOnErrorContainer = Color(0xFF7F1D1D)

// ── Chat-Specific Colors ────────────────────────────────────────────────────

// User bubble gradient (violet-to-pink)
val UserBubbleGradientStart = ElectricViolet    // #A855F7
val UserBubbleGradientEnd = HotPink             // #EC4899
val UserBubbleOnContent = Color.White

// Assistant bubble — dark glass morphism
val AssistantBubbleDark = Color(0xFF161622)      // Glass-like dark surface
val AssistantBubbleBorder = Color(0xFF2A2A3C)    // Subtle glass edge
val AssistantBubbleLight = Color(0xFFF3F2FA)     // Light theme variant
val AssistantBubbleLightBorder = Color(0xFFD6D3E8)

// Tool/function badge
val ToolBadgeColor = NeonCyan
val ToolBadgeBackground = NeonCyanSubtle

// Streaming cursor color
val StreamingCursorColor = ElectricVioletLight

// ── Gradient Presets ────────────────────────────────────────────────────────

/** Premium brand gradient stops */
val BrandGradientStart = ElectricViolet         // #A855F7
val BrandGradientMid = HotPink                  // #EC4899
val BrandGradientEnd = NeonCyan                 // #22D3EE

/** Subtle surface gradient for dark glass cards */
val GlassGradientStart = Color(0xFF12121A)
val GlassGradientEnd = Color(0xFF1A1428)        // Slight violet tint

/** Success gradient */
val SuccessGradientStart = BrightEmerald
val SuccessGradientEnd = NeonCyan
