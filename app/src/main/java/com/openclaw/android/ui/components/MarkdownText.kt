package com.openclaw.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Simple markdown renderer for chat messages.
 * Supports: **bold**, *italic*, `code`, ```code blocks```, # headers, - lists, [links](url)
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = parseMarkdownBlocks(markdown)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(8.dp),
                    )
                }

                is MdBlock.Heading -> {
                    Text(
                        text = block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.labelLarge
                        },
                        color = color,
                        fontWeight = FontWeight.Bold,
                    )
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }

                is MdBlock.ListItem -> {
                    Row {
                        Text("  \u2022 ", color = color, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = parseInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val language: String = "") : MdBlock()
    data class Heading(val text: String, val level: Int) : MdBlock()
    data class ListItem(val text: String) : MdBlock()
}

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.split("\n")
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            i++ // skip closing ```
            continue
        }

        // Heading
        val headingMatch = Regex("^(#{1,3})\\s+(.+)$").find(line)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            blocks.add(MdBlock.Heading(headingMatch.groupValues[2], level))
            i++
            continue
        }

        // List item
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") ||
            Regex("^\\d+\\.\\s").containsMatchIn(line.trimStart())
        ) {
            val itemText = line.trimStart().replaceFirst(Regex("^[-*]\\s|^\\d+\\.\\s"), "")
            blocks.add(MdBlock.ListItem(itemText))
            i++
            continue
        }

        // Empty line
        if (line.isBlank()) {
            i++
            continue
        }

        // Paragraph — collect consecutive non-blank, non-special lines
        val paraLines = mutableListOf(line)
        i++
        while (i < lines.size &&
            lines[i].isNotBlank() &&
            !lines[i].trimStart().startsWith("```") &&
            !lines[i].trimStart().startsWith("#") &&
            !lines[i].trimStart().startsWith("- ") &&
            !lines[i].trimStart().startsWith("* ")
        ) {
            paraLines.add(lines[i])
            i++
        }
        blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
    }

    return blocks
}

private fun parseInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var remaining = text
        while (remaining.isNotEmpty()) {
            // Bold
            val boldMatch = Regex("^\\*\\*(.+?)\\*\\*").find(remaining)
            if (boldMatch != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldMatch.groupValues[1])
                }
                remaining = remaining.substring(boldMatch.range.last + 1)
                continue
            }

            // Italic
            val italicMatch = Regex("^\\*(.+?)\\*").find(remaining)
            if (italicMatch != null) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(italicMatch.groupValues[1])
                }
                remaining = remaining.substring(italicMatch.range.last + 1)
                continue
            }

            // Inline code
            val codeMatch = Regex("^`([^`]+)`").find(remaining)
            if (codeMatch != null) {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                    append(codeMatch.groupValues[1])
                }
                remaining = remaining.substring(codeMatch.range.last + 1)
                continue
            }

            // Regular character
            append(remaining[0])
            remaining = remaining.substring(1)
        }
    }
}
