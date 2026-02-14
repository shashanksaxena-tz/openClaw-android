package com.openclaw.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------
// Glass Design System colors
// ---------------------------------------------------------------------------

/** Electric violet -- primary accent. */
val ElectricViolet = Color(0xFFA855F7)

/** Neon cyan -- secondary accent. */
val NeonCyan = Color(0xFF22D3EE)

/** Hot pink -- tertiary accent. */
val HotPink = Color(0xFFEC4899)

/** True black background. */
val TrueBlack = Color(0xFF050508)

/** Charcoal surface. */
val Charcoal = Color(0xFF0D0D12)

// ---------------------------------------------------------------------------
// GlassCard composable
// ---------------------------------------------------------------------------

/**
 * A glass-morphism card with a semi-transparent surface, subtle border, rounded
 * corners, and a soft shadow. Intended as the primary container component of
 * the new dark-first design system.
 *
 * @param modifier          Modifier applied to the outer [Column].
 * @param cornerRadius      Corner radius for the card shape (default 20 dp).
 * @param backgroundOpacity Opacity of the surface color fill (0f..1f, default
 *                          0.70 for dark, 0.60 for light).
 * @param content           Slot content laid out inside a [Column].
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    backgroundOpacity: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val resolvedOpacity = backgroundOpacity
        ?: if (isDark) 0.70f else 0.60f

    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = resolvedOpacity)

    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Gray.copy(alpha = 0.10f)
    }

    Column(
        modifier = modifier
            .shadow(
                elevation = 4.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = Color.Black.copy(alpha = 0.15f),
            )
            .clip(shape)
            .background(surfaceColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(16.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Modifier.glassSurface()
// ---------------------------------------------------------------------------

/**
 * Applies a glass-morphism surface effect to any composable: clipped rounded
 * corners, semi-transparent background, and a subtle border.
 *
 * @param cornerRadius      Corner radius (default 20 dp).
 * @param backgroundOpacity Surface fill opacity (default 0.70 dark / 0.60 light).
 */
fun Modifier.glassSurface(
    cornerRadius: Dp = 20.dp,
    backgroundOpacity: Float? = null,
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val resolvedOpacity = backgroundOpacity
        ?: if (isDark) 0.70f else 0.60f

    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = resolvedOpacity)

    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Gray.copy(alpha = 0.10f)
    }

    this
        .clip(shape)
        .background(surfaceColor)
        .border(width = 1.dp, color = borderColor, shape = shape)
}

// ---------------------------------------------------------------------------
// Modifier.glowShadow()
// ---------------------------------------------------------------------------

/**
 * Draws a colored outer-glow behind the composable by rendering a blurred
 * rounded rectangle in [color].
 *
 * @param color        Glow color (default: [ElectricViolet]).
 * @param cornerRadius Corner radius matching the target shape (default 20 dp).
 * @param glowRadius   How far the glow extends (default 8 dp).
 * @param alpha        Glow opacity (default 0.35).
 */
fun Modifier.glowShadow(
    color: Color = ElectricViolet,
    cornerRadius: Dp = 20.dp,
    glowRadius: Dp = 8.dp,
    alpha: Float = 0.35f,
): Modifier = this
    .shadow(
        elevation = glowRadius,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = color.copy(alpha = alpha),
        spotColor = color.copy(alpha = alpha),
    )

// ---------------------------------------------------------------------------
// Modifier.neonBorder()
// ---------------------------------------------------------------------------

/**
 * Draws an animated neon-glow border around the composable. The border color
 * subtly pulses between the supplied [color] and a brighter variant, creating
 * a neon sign effect.
 *
 * @param color        Base neon color (default: [NeonCyan]).
 * @param cornerRadius Corner radius for the border shape (default 20 dp).
 * @param borderWidth  Stroke width of the neon border (default 1.5 dp).
 */
fun Modifier.neonBorder(
    color: Color = NeonCyan,
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.5.dp,
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "neonPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "neonAlpha",
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "neonGlow",
    )

    val strokeWidthPx = borderWidth.value
    val cornerPx = cornerRadius.value

    this.drawBehind {
        // Outer glow layer -- a wider, more transparent stroke behind the main
        // border to simulate light bleed.
        drawRoundRect(
            color = color.copy(alpha = glowAlpha),
            topLeft = Offset(-strokeWidthPx * 2, -strokeWidthPx * 2),
            size = Size(
                size.width + strokeWidthPx * 4,
                size.height + strokeWidthPx * 4,
            ),
            cornerRadius = CornerRadius(cornerPx + strokeWidthPx * 2),
            style = Stroke(width = strokeWidthPx * 3),
        )

        // Main neon border.
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    color.copy(alpha = pulseAlpha),
                    color.copy(alpha = pulseAlpha * 0.7f),
                    color.copy(alpha = pulseAlpha),
                ),
            ),
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerPx),
            style = Stroke(width = strokeWidthPx),
        )
    }
}
