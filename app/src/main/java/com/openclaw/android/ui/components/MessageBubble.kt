package com.openclaw.android.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.openclaw.android.agent.AgentEvent

@Composable
fun MessageBubble(event: AgentEvent, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(
            animationSpec = tween(300)
        ),
    ) {
        when (event) {
            is AgentEvent.UserMessage -> UserBubble(event, modifier)
            is AgentEvent.AssistantMessage -> AssistantBubble(event.text, modifier)
            is AgentEvent.ToolCallStart -> ToolCallBubble(event, modifier)
            is AgentEvent.ToolCallResult -> ToolResultBubble(event, modifier)
            is AgentEvent.Error -> ErrorBubble(event.message, modifier)
            is AgentEvent.StreamChunk -> AssistantBubble(event.fullText, modifier)
            is AgentEvent.ModelSelected -> ModelBadge(event.modelName, modifier)
            else -> {}
        }
    }
}

@Composable
private fun UserBubble(event: AgentEvent.UserMessage, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(20.dp, 4.dp, 20.dp, 20.dp))
                .background(MaterialTheme.colorScheme.primary)
                .padding(12.dp),
        ) {
            if (event.media.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${event.media.size} attachment(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            if (event.text.isNotBlank()) {
                Text(
                    text = event.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AssistantBubble(text: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(4.dp, 20.dp, 20.dp, 20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
                .animateContentSize(),
        ) {
            MarkdownText(
                markdown = text,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

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
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Build,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = event.toolName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ToolResultBubble(event: AgentEvent.ToolCallResult, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = if (expanded || event.result.length <= 200) event.result
        else event.result.take(200) + "..."

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (event.isError)
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerLow
                )
                .padding(10.dp)
                .animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (event.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (event.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = event.toolName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (event.result.length > 200) {
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.height(20.dp),
                    ) {
                        Text(
                            if (expanded) "less" else "more",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodySmall,
                color = if (event.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ErrorBubble(message: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ModelBadge(modelName: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Text(
                text = modelName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
    }
}
