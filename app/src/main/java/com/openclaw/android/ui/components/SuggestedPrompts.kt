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
        text = "Daily briefing",
        icon = Icons.Default.WbSunny,
        prompt = "Give me my daily briefing",
    ),
    PromptSuggestion(
        text = "Add a task",
        icon = Icons.Default.CheckCircle,
        prompt = "Create a high priority task to ",
    ),
    PromptSuggestion(
        text = "Plan a trip",
        icon = Icons.Default.FlightTakeoff,
        prompt = "Help me plan a trip to ",
    ),
    PromptSuggestion(
        text = "Team dashboard",
        icon = Icons.Default.Groups,
        prompt = "Show me my team dashboard",
    ),
    PromptSuggestion(
        text = "Capture a note",
        icon = Icons.Default.EditNote,
        prompt = "Note: ",
    ),
    PromptSuggestion(
        text = "My priorities",
        icon = Icons.Default.PriorityHigh,
        prompt = "What are my top priorities today?",
    ),
    PromptSuggestion(
        text = "Log a decision",
        icon = Icons.Default.Gavel,
        prompt = "I decided to ",
    ),
    PromptSuggestion(
        text = "Productivity tips",
        icon = Icons.Default.TrendingUp,
        prompt = "Give me productivity suggestions based on my patterns",
    ),
    PromptSuggestion(
        text = "Set a reminder",
        icon = Icons.Default.NotificationAdd,
        prompt = "Remind me to ",
    ),
    PromptSuggestion(
        text = "Weekly report",
        icon = Icons.Default.Assessment,
        prompt = "Show me my weekly productivity report",
    ),
    PromptSuggestion(
        text = "Search the web",
        icon = Icons.Default.TravelExplore,
        prompt = "Search the web for ",
    ),
    PromptSuggestion(
        text = "Call a contact",
        icon = Icons.Default.Phone,
        prompt = "Call ",
    ),
    PromptSuggestion(
        text = "Read my texts",
        icon = Icons.Default.Sms,
        prompt = "Read my recent text messages",
    ),
    PromptSuggestion(
        text = "Track a habit",
        icon = Icons.Default.FitnessCenter,
        prompt = "Track a habit: ",
    ),
    PromptSuggestion(
        text = "What do you know?",
        icon = Icons.Default.Psychology,
        prompt = "What do you remember about me?",
    ),
    PromptSuggestion(
        text = "Take a screenshot",
        icon = Icons.Default.Screenshot,
        prompt = "Take a screenshot of my screen",
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
