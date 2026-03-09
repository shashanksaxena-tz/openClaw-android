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
// Clean Design System colors
// ---------------------------------------------------------------------------

/** Primary accent — mapped to system gray for clean look. */
val ElectricViolet = Color(0xFF8E8E93)

/** Secondary accent — mapped to brand teal. */
val NeonCyan = Color(0xFF2AC4A0)

/** Tertiary accent — mapped to error red. */
val HotPink = Color(0xFFFF3B30)

/** Background. */
val TrueBlack = Color(0xFF000000)

/** Surface. */
val Charcoal = Color(0xFF1C1C1E)

// ---------------------------------------------------------------------------
// CleanCard composable
// ---------------------------------------------------------------------------

/**
 * A clean card with a subtle border and rounded corners.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    backgroundOpacity: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    Column(
        modifier = modifier
            .shadow(
                elevation = 1.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.08f),
                spotColor = Color.Black.copy(alpha = 0.04f),
            )
            .clip(shape)
            .background(surfaceColor)
            .border(width = 0.5.dp, color = borderColor, shape = shape)
            .padding(16.dp),
        content = content,
    )
}

// ---------------------------------------------------------------------------
// Modifier.glassSurface()
// ---------------------------------------------------------------------------

fun Modifier.glassSurface(
    cornerRadius: Dp = 16.dp,
    backgroundOpacity: Float? = null,
): Modifier = composed {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.06f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    this
        .clip(shape)
        .background(surfaceColor)
        .border(width = 0.5.dp, color = borderColor, shape = shape)
}

// ---------------------------------------------------------------------------
// Modifier.glowShadow() — now a subtle shadow
// ---------------------------------------------------------------------------

fun Modifier.glowShadow(
    color: Color = Color.Black,
    cornerRadius: Dp = 16.dp,
    glowRadius: Dp = 4.dp,
    alpha: Float = 0.08f,
): Modifier = this
    .shadow(
        elevation = glowRadius,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = color.copy(alpha = alpha),
        spotColor = color.copy(alpha = alpha),
    )

// ---------------------------------------------------------------------------
// Modifier.neonBorder() — now a subtle highlight border
// ---------------------------------------------------------------------------

fun Modifier.neonBorder(
    color: Color = Color(0xFFE5E5EA),
    cornerRadius: Dp = 16.dp,
    borderWidth: Dp = 0.5.dp,
): Modifier = composed {
    this.border(
        width = borderWidth,
        color = color,
        shape = RoundedCornerShape(cornerRadius),
    )
}
