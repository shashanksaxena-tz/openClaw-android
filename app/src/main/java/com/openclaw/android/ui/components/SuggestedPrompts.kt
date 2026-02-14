package com.openclaw.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Design system colors (ElectricViolet, NeonCyan from ClayCard.kt)
private val MutedViolet = Color(0xFF9B8AB8)

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
 * Horizontally scrollable row of glass-styled suggested prompt chips.
 * Shown on empty chat state to help new users get started.
 * Premium dark-first design with glass morphism aesthetic.
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
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
            ),
            color = MutedViolet,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            suggestions.forEachIndexed { index, suggestion ->
                GlassSuggestionChip(
                    suggestion = suggestion,
                    onClick = { onSuggestionClick(suggestion.prompt) },
                    // Alternate icon tint between cyan and violet
                    iconTint = if (index % 2 == 0) NeonCyan else ElectricViolet,
                )
            }
        }
    }
}

@Composable
private fun GlassSuggestionChip(
    suggestion: PromptSuggestion,
    onClick: () -> Unit,
    iconTint: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "chipScale",
    )

    val chipShape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(chipShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.60f))
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = chipShape,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = suggestion.icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = suggestion.text,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Normal,
            ),
            color = Color.White,
        )
    }
}
