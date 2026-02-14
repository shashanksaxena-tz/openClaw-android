package com.openclaw.android.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// ── Design tokens ────────────────────────────────────────────────────────────
private val Violet = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)
private val CodeBlockBg = Color(0xFF0A0A12)
private val InlineCodeBg = Color(0xFF1A1A28)
private val InlineCodeBorder = Color(0xFF2A2A3C)

/**
 * Markdown renderer for chat messages.
 * Supports: **bold**, *italic*, `code`, ```code blocks```, # headers, - lists,
 * and ```mermaid diagrams via WebView.
 *
 * Styled with a dark-first, glass-morphism aesthetic:
 *   primary  = electric violet (#A855F7)
 *   secondary = neon cyan (#22D3EE)
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    val bgColor = MaterialTheme.colorScheme.surface
    val textColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.MermaidBlock -> {
                    MermaidDiagram(
                        code = block.code,
                        bgColor = bgColor,
                        textColor = textColor,
                    )
                }

                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 0.5.dp,
                                color = Violet.copy(alpha = 0.20f),
                                shape = RoundedCornerShape(12.dp),
                            )
                            .background(CodeBlockBg)
                            .padding(12.dp),
                    ) {
                        // Language label (top-end corner)
                        if (block.language.isNotBlank()) {
                            Text(
                                text = block.language,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.5.sp,
                                ),
                                color = Violet.copy(alpha = 0.55f),
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }

                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                            ),
                            color = Cyan.copy(alpha = 0.85f),
                            modifier = if (block.language.isNotBlank()) {
                                Modifier.padding(top = 14.dp)
                            } else {
                                Modifier
                            },
                        )
                    }
                }

                is MdBlock.Heading -> {
                    when (block.level) {
                        1 -> {
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    brush = Brush.linearGradient(
                                        colors = listOf(Violet, Cyan),
                                    ),
                                ),
                            )
                        }
                        2 -> {
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.titleMedium,
                                color = Violet,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        else -> {
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.labelLarge,
                                color = Violet.copy(alpha = 0.80f),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                is MdBlock.Paragraph -> {
                    Text(
                        text = parseInlineMarkdown(block.text, color),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                        ),
                        color = color,
                    )
                }

                is MdBlock.ListItem -> {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp, end = 8.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Violet),
                        )
                        Text(
                            text = parseInlineMarkdown(block.text, color),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                            ),
                            color = color,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MermaidDiagram(
    code: String,
    bgColor: Color,
    textColor: Color,
) {
    val bgHex = String.format("#%06X", 0xFFFFFF and bgColor.toArgb())
    val textHex = String.format("#%06X", 0xFFFFFF and textColor.toArgb())

    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script src="https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"></script>
            <style>
                body { margin: 0; padding: 8px; background: #000000; overflow: hidden; }
                .mermaid { color: $textHex; }
                .mermaid svg { max-width: 100%; height: auto; }
            </style>
        </head>
        <body>
            <pre class="mermaid">
            $code
            </pre>
            <script>
                mermaid.initialize({
                    startOnLoad: true,
                    theme: 'dark',
                    themeVariables: {
                        primaryColor: '#A855F7',
                        primaryTextColor: '$textHex',
                        primaryBorderColor: '#A855F7',
                        lineColor: '#22D3EE',
                        secondaryColor: '#1A1A28',
                        tertiaryColor: '#0A0A12',
                        background: '#000000',
                        mainBkg: '#0A0A12',
                        nodeBorder: '#A855F7',
                        clusterBkg: '#0A0A12',
                        clusterBorder: '#A855F7',
                        titleColor: '#22D3EE',
                        edgeLabelBackground: '#0A0A12',
                    }
                });
            </script>
        </body>
        </html>
    """.trimIndent()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                setBackgroundColor(android.graphics.Color.BLACK)
                webViewClient = WebViewClient()
                loadDataWithBaseURL("https://cdn.jsdelivr.net", html, "text/html", "UTF-8", null)
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 400.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 0.5.dp,
                color = Violet.copy(alpha = 0.20f),
                shape = RoundedCornerShape(12.dp),
            ),
    )
}

private sealed class MdBlock {
    data class Paragraph(val text: String) : MdBlock()
    data class CodeBlock(val code: String, val language: String = "") : MdBlock()
    data class MermaidBlock(val code: String) : MdBlock()
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
            val code = codeLines.joinToString("\n")
            if (lang.equals("mermaid", ignoreCase = true)) {
                blocks.add(MdBlock.MermaidBlock(code))
            } else {
                blocks.add(MdBlock.CodeBlock(code, lang))
            }
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

        // Paragraph
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

private fun parseInlineMarkdown(
    text: String,
    baseColor: Color = Color.Unspecified,
): AnnotatedString {
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
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = Cyan.copy(alpha = 0.85f),
                        background = InlineCodeBg,
                    ),
                ) {
                    append("\u2009${codeMatch.groupValues[1]}\u2009")
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
