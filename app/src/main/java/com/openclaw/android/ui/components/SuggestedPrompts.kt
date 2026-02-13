package com.openclaw.android.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

data class PromptSuggestion(
    val text: String,
    val icon: ImageVector,
    val prompt: String,
)

val defaultSuggestions = listOf(
    PromptSuggestion(
        text = "Write something",
        icon = Icons.Default.Edit,
        prompt = "Help me write ",
    ),
    PromptSuggestion(
        text = "Summarize",
        icon = Icons.Default.Summarize,
        prompt = "Summarize this: ",
    ),
    PromptSuggestion(
        text = "Create a file",
        icon = Icons.Default.NoteAdd,
        prompt = "Create a file called ",
    ),
    PromptSuggestion(
        text = "Search the web",
        icon = Icons.Default.Search,
        prompt = "Search the web for ",
    ),
    PromptSuggestion(
        text = "Brainstorm ideas",
        icon = Icons.Default.Lightbulb,
        prompt = "Help me brainstorm ideas for ",
    ),
    PromptSuggestion(
        text = "Make a list",
        icon = Icons.Default.FormatListBulleted,
        prompt = "Create a list of ",
    ),
)

/**
 * Horizontally scrollable row of suggested prompt chips.
 * Shown on empty chat state to help new users get started.
 */
@Composable
fun SuggestedPrompts(
    onSuggestionClick: (String) -> Unit,
    suggestions: List<PromptSuggestion> = defaultSuggestions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = "Try asking...",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (suggestion in suggestions) {
                SuggestionChip(
                    onClick = { onSuggestionClick(suggestion.prompt) },
                    label = { Text(suggestion.text) },
                    icon = {
                        Icon(
                            suggestion.icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        iconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(
                        enabled = true,
                        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    ),
                )
            }
        }
    }
}
