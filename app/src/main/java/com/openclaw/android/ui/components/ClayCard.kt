package com.openclaw.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Claymorphism modifier: soft outer shadow + subtle inner shadow for a 3D "clay" feel.
 * Use this on any Surface/Box to get the puffy clay effect.
 */
fun Modifier.clayShadow(
    cornerRadius: Dp = 18.dp,
    elevation: Dp = 6.dp,
    outerShadowColor: Color = Color(0x18000000),
    innerShadowColor: Color = Color(0x10000000),
    highlightColor: Color = Color(0x20FFFFFF),
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = outerShadowColor,
        spotColor = outerShadowColor,
    )
    .drawBehind {
        val radius = cornerRadius.toPx()

        // Inner shadow (top-left highlight, bottom-right shadow)
        // Top-left highlight
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(highlightColor, Color.Transparent),
                start = Offset(0f, 0f),
                end = Offset(size.width * 0.5f, size.height * 0.5f),
            ),
            cornerRadius = CornerRadius(radius),
            size = size,
        )

        // Bottom-right shadow
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, innerShadowColor),
                start = Offset(size.width * 0.5f, size.height * 0.5f),
                end = Offset(size.width, size.height),
            ),
            cornerRadius = CornerRadius(radius),
            size = size,
        )
    }

/**
 * ClayCard: a pre-styled claymorphism container.
 */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clayShadow(cornerRadius = cornerRadius)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .padding(16.dp),
        content = content,
    )
}

/**
 * Modifier for the user message bubble clay effect (primary color).
 */
fun Modifier.clayBubbleUser(
    cornerRadius: Dp = 20.dp,
): Modifier = this.clayShadow(
    cornerRadius = cornerRadius,
    elevation = 4.dp,
    outerShadowColor = Color(0x20000000),
    innerShadowColor = Color(0x15000000),
    highlightColor = Color(0x30FFFFFF),
)

/**
 * Modifier for the assistant message bubble clay effect (surface variant).
 */
fun Modifier.clayBubbleAssistant(
    cornerRadius: Dp = 20.dp,
): Modifier = this.clayShadow(
    cornerRadius = cornerRadius,
    elevation = 3.dp,
    outerShadowColor = Color(0x15000000),
    innerShadowColor = Color(0x0D000000),
    highlightColor = Color(0x25FFFFFF),
)
