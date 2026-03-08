package com.openclaw.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class PromptSuggestion(
    val text: String,
    val icon: ImageVector,
    val prompt: String,
)

val defaultSuggestions = listOf(
    PromptSuggestion(
        text = "Plan",
        icon = Icons.Default.WbSunny,
        prompt = "Give me my daily briefing",
    ),
    PromptSuggestion(
        text = "Explain",
        icon = Icons.Default.AutoAwesome,
        prompt = "Explain a complex topic simply: ",
    ),
    PromptSuggestion(
        text = "Design",
        icon = Icons.Default.Palette,
        prompt = "Help me design ",
    ),
    PromptSuggestion(
        text = "Write",
        icon = Icons.Default.EditNote,
        prompt = "Help me write ",
    ),
    PromptSuggestion(
        text = "Organize",
        icon = Icons.Default.CheckCircle,
        prompt = "Help me organize my tasks for today",
    ),
    PromptSuggestion(
        text = "Research",
        icon = Icons.Default.TravelExplore,
        prompt = "Search the web for ",
    ),
)

/**
 * Horizontally scrollable row of clean suggestion chips.
 * Shown in empty chat state — matches reference design with compact chip cards.
 */
@Composable
fun SuggestedPrompts(
    onSuggestionClick: (String) -> Unit,
    suggestions: List<PromptSuggestion> = defaultSuggestions,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            SuggestionChipCard(
                suggestion = suggestion,
                onClick = { onSuggestionClick(suggestion.prompt) },
            )
        }
    }
}

@Composable
private fun SuggestionChipCard(
    suggestion: PromptSuggestion,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chipScale",
    )

    Column(
        modifier = Modifier
            .width(120.dp)
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = suggestion.text,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = suggestion.prompt.take(30).trimEnd(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
