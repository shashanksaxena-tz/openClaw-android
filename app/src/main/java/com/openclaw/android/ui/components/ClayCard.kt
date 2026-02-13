package com.openclaw.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Modern subtle shadow modifier for cards and bubbles.
 */
fun Modifier.modernShadow(
    cornerRadius: Dp = 16.dp,
    elevation: Dp = 2.dp,
): Modifier = this
    .shadow(
        elevation = elevation,
        shape = RoundedCornerShape(cornerRadius),
        ambientColor = Color(0x0A000000),
        spotColor = Color(0x12000000),
    )

/**
 * ModernCard: a clean, minimal container.
 */
@Composable
fun ClayCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .modernShadow(cornerRadius = cornerRadius)
            .clip(RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .padding(16.dp),
        content = content,
    )
}

/**
 * Modifier for user message bubble.
 */
fun Modifier.clayBubbleUser(
    cornerRadius: Dp = 20.dp,
): Modifier = this.modernShadow(
    cornerRadius = cornerRadius,
    elevation = 1.dp,
)

/**
 * Modifier for assistant message bubble.
 */
fun Modifier.clayBubbleAssistant(
    cornerRadius: Dp = 20.dp,
): Modifier = this.modernShadow(
    cornerRadius = cornerRadius,
    elevation = 0.5.dp,
)
