package com.openclaw.android.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openclaw.android.agent.AgentEvent

// ── Design Tokens ──────────────────────────────────────────────────────────────

private val UserBubbleDark = Color(0xFF2C2C2E)
private val UserBubbleLight = Color(0xFFE5E5EA)
private val ErrorRed = Color(0xFFFF3B30)
private val ErrorBgDark = Color(0xFF2C1B1B)
private val ErrorBgLight = Color(0xFFFDEDED)
private val ToolAccent = Color(0xFF8E8E93)   // iOS system gray
private val Emerald = Color(0xFF34C759)      // iOS system green
private val EscalationAmber = Color(0xFFFF9F0A)

private val UserBubbleShape = RoundedCornerShape(20.dp)
private val ErrorCardShape = RoundedCornerShape(12.dp)
private val PillShape = RoundedCornerShape(8.dp)

// ── Clipboard helper ─────────────────────────────────────────────────────────

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw", text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

// ── Public Entry Point ─────────────────────────────────────────────────────────

@Composable
fun MessageBubble(
    event: AgentEvent,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(200)),
    ) {
        when (event) {
            is AgentEvent.UserMessage -> UserBubble(event, modifier)
            is AgentEvent.AssistantMessage -> AssistantBubble(event.text, modifier)
            is AgentEvent.ToolCallStart -> ToolCallBubble(event, modifier)
            is AgentEvent.ToolCallResult -> ToolResultBubble(event, modifier)
            is AgentEvent.Error -> ErrorBubble(event.message, onRetry, modifier)
            is AgentEvent.StreamChunk -> StreamChunkBubble(event.chunk, modifier)
            is AgentEvent.ModelSelected -> ModelBadge(event.modelName, modifier)
            is AgentEvent.Escalation -> EscalationBadge(event.from, event.to, modifier)
            else -> {}
        }
    }
}

// ── 1. User Bubble ─────────────────────────────────────────────────────────────
// Right-aligned, subtle rounded rectangle. Long-press to copy.

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(event: AgentEvent.UserMessage, modifier: Modifier) {
    val isDark = isSystemInDarkTheme()
    val bubbleColor = if (isDark) UserBubbleDark else UserBubbleLight
    val textColor = if (isDark) Color.White else Color.Black
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(UserBubbleShape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        if (event.text.isNotBlank()) copyToClipboard(context, event.text)
                    },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (event.media.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = textColor.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${event.media.size} attachment${if (event.media.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.5f),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            if (event.text.isNotBlank()) {
                Text(
                    text = event.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                )
            }
        }
    }
}

// ── 2. Assistant Bubble ────────────────────────────────────────────────────────
// Clean markdown text with a copy button that appears on hover/tap.

@Composable
private fun AssistantBubble(text: String, modifier: Modifier) {
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
            ),
    ) {
        MarkdownText(
            markdown = text,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // Copy action row — always visible below assistant messages
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.Start,
        ) {
            IconButton(
                onClick = { copyToClipboard(context, text) },
                modifier = Modifier.size(28.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy message",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
        }
    }
}

// ── 3. Tool Call Bubble ────────────────────────────────────────────────────────
// Minimal inline pill, collapsed feel. Muted colors.

@Composable
private fun ToolCallBubble(event: AgentEvent.ToolCallStart, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Terminal,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = event.toolName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    fontSize = 11.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ── 4. Tool Result Bubble ──────────────────────────────────────────────────────
// Collapsed by default. Minimal presentation with expand toggle.

@Composable
private fun ToolResultBubble(event: AgentEvent.ToolCallResult, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val needsTruncation = event.result.length > 200
    val displayText = if (expanded || !needsTruncation) event.result
    else event.result.take(200) + "\u2026"

    val accentColor = if (event.isError) ErrorRed else Emerald

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable(enabled = needsTruncation) { expanded = !expanded }
                .padding(10.dp)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 400f),
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (event.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = accentColor.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = event.toolName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                if (needsTruncation) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (expanded) "less" else "more",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accentColor.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                ),
                color = if (event.isError) ErrorRed.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

// ── 5. Error Bubble ────────────────────────────────────────────────────────────
// Clean red card. No heavy shadows.

@Composable
private fun ErrorBubble(message: String, onRetry: (() -> Unit)?, modifier: Modifier) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) ErrorBgDark else ErrorBgLight

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(ErrorCardShape)
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    modifier = Modifier.size(16.dp),
                    tint = ErrorRed,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = ErrorRed.copy(alpha = 0.9f),
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (onRetry != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRetry,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ErrorRed,
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Retry",
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
    }
}

// ── 6. Stream Chunk Bubble ─────────────────────────────────────────────────────
// Same as assistant: no bubble, just clean markdown text flowing in.

@Composable
private fun StreamChunkBubble(fullText: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
            ),
    ) {
        MarkdownText(
            markdown = fullText,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

// ── 8. Escalation Badge ────────────────────────────────────────────────────────

@Composable
private fun EscalationBadge(from: String, to: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(PillShape)
                .background(EscalationAmber.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(11.dp),
                tint = EscalationAmber.copy(alpha = 0.65f),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = "Escalating to $to",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                ),
                color = EscalationAmber.copy(alpha = 0.6f),
            )
        }
    }
}
