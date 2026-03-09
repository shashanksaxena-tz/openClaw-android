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
// Semantic colors that don't change with theme
private val Emerald = Color(0xFF34C759)
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

// ── 1. User Bubble ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(event: AgentEvent.UserMessage, modifier: Modifier) {
    val bubbleColor = MaterialTheme.colorScheme.surfaceVariant
    val textColor = MaterialTheme.colorScheme.onSurface
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantBubble(text: String, modifier: Modifier) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { if (text.isNotBlank()) copyToClipboard(context, text) },
            )
            .animateContentSize(
                animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
            ),
    ) {
        MarkdownText(
            markdown = text,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── 3. Tool Call Bubble ────────────────────────────────────────────────────────

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

@Composable
private fun ErrorBubble(message: String, onRetry: (() -> Unit)?, modifier: Modifier) {
    val errorColor = MaterialTheme.colorScheme.error
    val bgColor = MaterialTheme.colorScheme.errorContainer
    val textColor = MaterialTheme.colorScheme.onErrorContainer

    val parts = message.split("\n\n")
    val mainMessage = parts.firstOrNull() ?: message
    val hasSettingsAction = parts.getOrNull(1)?.contains("Settings", ignoreCase = true) == true

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .clip(ErrorCardShape)
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = "Error",
                    modifier = Modifier.size(16.dp),
                    tint = errorColor,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = mainMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    lineHeight = 18.sp,
                )
            }

            if (hasSettingsAction) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Go to Settings to fix this.",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = errorColor.copy(alpha = 0.75f),
                )
            }

            if (onRetry != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = errorColor),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

// ── 6. Stream Chunk Bubble ─────────────────────────────────────────────────────

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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
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
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.4.sp),
                color = EscalationAmber.copy(alpha = 0.6f),
            )
        }
    }
}
