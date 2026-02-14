package com.openclaw.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.agent.AgentEvent

// ── Design Tokens ──────────────────────────────────────────────────────────────

// HotPink, NeonCyan from ClayCard.kt
private val Violet = Color(0xFFA855F7)
private val Emerald = Color(0xFF34D399)
private val ErrorRed = Color(0xFFEF4444)
private val DarkSurface = Color(0xFF161622)
private val GlassBorder = Color.White.copy(alpha = 0.06f)

private val UserGradient = Brush.horizontalGradient(listOf(Violet, HotPink))
private val RetryGradient = Brush.horizontalGradient(listOf(Violet, HotPink))

private val UserBubbleShape = RoundedCornerShape(
    topStart = 20.dp, topEnd = 20.dp, bottomEnd = 4.dp, bottomStart = 20.dp,
)
private val AssistantBubbleShape = RoundedCornerShape(
    topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 4.dp,
)
private val ToolPillShape = RoundedCornerShape(12.dp)
private val ErrorCardShape = RoundedCornerShape(16.dp)
private val ModelPillShape = RoundedCornerShape(8.dp)

// ── Public Entry Point ─────────────────────────────────────────────────────────

@Composable
fun MessageBubble(
    event: AgentEvent,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(
            initialOffsetY = { it / 3 },
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 400f),
        ) + fadeIn(animationSpec = tween(220)),
    ) {
        when (event) {
            is AgentEvent.UserMessage -> UserBubble(event, modifier)
            is AgentEvent.AssistantMessage -> AssistantBubble(event.text, modifier)
            is AgentEvent.ToolCallStart -> ToolCallBubble(event, modifier)
            is AgentEvent.ToolCallResult -> ToolResultBubble(event, modifier)
            is AgentEvent.Error -> ErrorBubble(event.message, onRetry, modifier)
            is AgentEvent.StreamChunk -> StreamChunkBubble(event.fullText, modifier)
            is AgentEvent.ModelSelected -> ModelBadge(event.modelName, modifier)
            else -> {}
        }
    }
}

// ── 1. User Bubble ─────────────────────────────────────────────────────────────

@Composable
private fun UserBubble(event: AgentEvent.UserMessage, modifier: Modifier) {
    val offsetX = remember { Animatable(80f) }
    LaunchedEffect(Unit) {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .offset { IntOffset(offsetX.value.toInt(), 0) },
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = UserBubbleShape,
                    ambientColor = Violet.copy(alpha = 0.35f),
                    spotColor = Violet.copy(alpha = 0.35f),
                )
                .clip(UserBubbleShape)
                .background(UserGradient)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (event.media.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = Color.White.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${event.media.size} attachment${if (event.media.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            if (event.text.isNotBlank()) {
                Text(
                    text = event.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}

// ── 2. Assistant Bubble ────────────────────────────────────────────────────────

@Composable
private fun AssistantBubble(text: String, modifier: Modifier) {
    val offsetX = remember { Animatable(-80f) }
    LaunchedEffect(Unit) {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .offset { IntOffset(offsetX.value.toInt(), 0) },
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(AssistantBubbleShape)
                .border(0.5.dp, GlassBorder, AssistantBubbleShape)
                .background(DarkSurface.copy(alpha = 0.90f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                ),
        ) {
            MarkdownText(
                markdown = text,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

// ── 3. Tool Call Bubble ────────────────────────────────────────────────────────

@Composable
private fun ToolCallBubble(event: AgentEvent.ToolCallStart, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .clip(ToolPillShape)
                .border(0.5.dp, NeonCyan.copy(alpha = 0.25f), ToolPillShape)
                .background(DarkSurface.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = NeonCyan,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = event.toolName,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                ),
                color = NeonCyan,
            )
        }
    }
}

// ── 4. Tool Result Bubble ──────────────────────────────────────────────────────

@Composable
private fun ToolResultBubble(event: AgentEvent.ToolCallResult, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val needsTruncation = event.result.length > 200
    val displayText = if (expanded || !needsTruncation) event.result
    else event.result.take(200) + "\u2026"

    val accentColor = if (event.isError) ErrorRed else Emerald
    val borderColor = accentColor.copy(alpha = 0.20f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(ToolPillShape)
                .border(0.5.dp, borderColor, ToolPillShape)
                .background(DarkSurface.copy(alpha = 0.85f))
                .clickable(enabled = needsTruncation) { expanded = !expanded }
                .padding(10.dp)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                ),
        ) {
            // Header row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (event.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = accentColor,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = event.toolName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White.copy(alpha = 0.55f),
                )
                if (needsTruncation) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (expanded) "less" else "more",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accentColor.copy(alpha = 0.80f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // Result body
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                ),
                color = if (event.isError) ErrorRed.copy(alpha = 0.85f)
                else Color.White.copy(alpha = 0.60f),
            )
        }
    }
}

// ── 5. Error Bubble ────────────────────────────────────────────────────────────

@Composable
private fun ErrorBubble(message: String, onRetry: (() -> Unit)?, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .shadow(
                    elevation = 8.dp,
                    shape = ErrorCardShape,
                    ambientColor = ErrorRed.copy(alpha = 0.20f),
                    spotColor = ErrorRed.copy(alpha = 0.20f),
                )
                .clip(ErrorCardShape)
                .border(0.5.dp, ErrorRed.copy(alpha = 0.25f), ErrorCardShape)
                .background(DarkSurface.copy(alpha = 0.92f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = ErrorRed,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed.copy(alpha = 0.90f),
                )
            }
            if (onRetry != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(RetryGradient),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color.White,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White,
                    )
                }
            }
        }
    }
}

// ── 6. Stream Chunk Bubble ─────────────────────────────────────────────────────

@Composable
private fun StreamChunkBubble(fullText: String, modifier: Modifier) {
    val offsetX = remember { Animatable(-80f) }
    LaunchedEffect(Unit) {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 350f),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .offset { IntOffset(offsetX.value.toInt(), 0) },
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(AssistantBubbleShape)
                .border(0.5.dp, GlassBorder, AssistantBubbleShape)
                .background(DarkSurface.copy(alpha = 0.90f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                ),
        ) {
            MarkdownText(
                markdown = fullText,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

// ── 7. Model Badge ─────────────────────────────────────────────────────────────

@Composable
private fun ModelBadge(modelName: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(ModelPillShape)
                .border(0.5.dp, GlassBorder, ModelPillShape)
                .background(DarkSurface.copy(alpha = 0.70f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = Violet.copy(alpha = 0.55f),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                ),
                color = Color.White.copy(alpha = 0.40f),
            )
        }
    }
}
