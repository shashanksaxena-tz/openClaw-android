package com.openclaw.android.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Markdown renderer for chat messages.
 * Supports: **bold**, *italic*, `code`, ```code blocks```, # headers, - lists,
 * and ```mermaid diagrams via WebView.
 *
 * Theme-aware: adapts colors to light/dark mode automatically.
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
    val uriHandler = LocalUriHandler.current

    // Theme-adaptive tokens
    val headingColor = MaterialTheme.colorScheme.onSurface
    val bulletColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.tertiary
    val codeBlockBg = MaterialTheme.colorScheme.surfaceVariant
    val codeBlockBorder = MaterialTheme.colorScheme.outlineVariant
    val codeLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val codeTextColor = MaterialTheme.colorScheme.onSurface
    val inlineCodeBg = MaterialTheme.colorScheme.surfaceVariant

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
                                color = codeBlockBorder,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .background(codeBlockBg)
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
                                color = codeLabelColor,
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
                            color = codeTextColor,
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
                                ),
                                color = headingColor,
                            )
                        }
                        2 -> {
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.titleMedium,
                                color = headingColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        else -> {
                            Text(
                                text = block.text,
                                style = MaterialTheme.typography.labelLarge,
                                color = headingColor,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                is MdBlock.Paragraph -> {
                    val annotated = parseInlineMarkdown(block.text, color, linkColor, inlineCodeBg)
                    ClickableText(
                        text = annotated,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            color = color,
                        ),
                        onClick = { offset ->
                            annotated.getStringAnnotations("URL", offset, offset)
                                .firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } }
                        },
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
                                .background(bulletColor),
                        )
                        val annotated = parseInlineMarkdown(block.text, color, linkColor, inlineCodeBg)
                        ClickableText(
                            text = annotated,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                lineHeight = 22.sp,
                                color = color,
                            ),
                            onClick = { offset ->
                                annotated.getStringAnnotations("URL", offset, offset)
                                    .firstOrNull()?.let { runCatching { uriHandler.openUri(it.item) } }
                            },
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
                body { margin: 0; padding: 8px; background: $bgHex; overflow: hidden; }
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
                    theme: 'neutral',
                    themeVariables: {
                        primaryColor: '#E5E5EA',
                        primaryTextColor: '$textHex',
                        primaryBorderColor: '#C7C7CC',
                        lineColor: '#8E8E93',
                        secondaryColor: '#F2F2F7',
                        tertiaryColor: '#FAFAFA',
                        background: '$bgHex',
                        mainBkg: '#F2F2F7',
                        nodeBorder: '#C7C7CC',
                        clusterBkg: '#F2F2F7',
                        clusterBorder: '#C7C7CC',
                        titleColor: '$textHex',
                        edgeLabelBackground: '$bgHex',
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
                // Security: restrict WebView to prevent XSS from crafted mermaid blocks
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.domStorageEnabled = false
                settings.databaseEnabled = false
                @Suppress("DEPRECATION")
                settings.allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                settings.allowUniversalAccessFromFileURLs = false
                setBackgroundColor(bgColor.toArgb())
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
                color = Color(0xFFE5E5EA),
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

private val LINK_REGEX = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
private val BARE_URL_REGEX = Regex("(https?://[^\\s)]+)")
private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
private val ITALIC_REGEX = Regex("\\*(.+?)\\*")
private val INLINE_CODE_REGEX = Regex("`([^`]+)`")

private fun parseInlineMarkdown(
    text: String,
    baseColor: Color = Color.Unspecified,
    linkColor: Color = Color(0xFF007AFF),
    inlineCodeBg: Color = Color(0xFFF2F2F7),
): AnnotatedString {
    return buildAnnotatedString {
        var pos = 0
        while (pos < text.length) {
            // Link: [text](url)
            val linkMatch = LINK_REGEX.find(text, pos)
            if (linkMatch != null && linkMatch.range.first == pos) {
                val linkText = linkMatch.groupValues[1]
                val url = linkMatch.groupValues[2]
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(linkText)
                }
                pop()
                pos = linkMatch.range.last + 1
                continue
            }

            // Bare URL: https://... or http://...
            val bareUrlMatch = BARE_URL_REGEX.find(text, pos)
            if (bareUrlMatch != null && bareUrlMatch.range.first == pos) {
                val url = bareUrlMatch.groupValues[1]
                pushStringAnnotation(tag = "URL", annotation = url)
                withStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)) {
                    append(url)
                }
                pop()
                pos = bareUrlMatch.range.last + 1
                continue
            }

            // Bold
            val boldMatch = BOLD_REGEX.find(text, pos)
            if (boldMatch != null && boldMatch.range.first == pos) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(boldMatch.groupValues[1])
                }
                pos = boldMatch.range.last + 1
                continue
            }

            // Italic
            val italicMatch = ITALIC_REGEX.find(text, pos)
            if (italicMatch != null && italicMatch.range.first == pos) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(italicMatch.groupValues[1])
                }
                pos = italicMatch.range.last + 1
                continue
            }

            // Inline code
            val codeMatch = INLINE_CODE_REGEX.find(text, pos)
            if (codeMatch != null && codeMatch.range.first == pos) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        background = inlineCodeBg,
                    ),
                ) {
                    append("\u2009${codeMatch.groupValues[1]}\u2009")
                }
                pos = codeMatch.range.last + 1
                continue
            }

            // Regular character — advance by index, no substring allocation
            append(text[pos])
            pos++
        }
    }
}
