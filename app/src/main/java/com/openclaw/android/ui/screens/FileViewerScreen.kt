package com.openclaw.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.openclaw.android.ui.components.MarkdownText
import java.io.File

// ── Design Tokens ────────────────────────────────────────────────────────────
private val TrueBlack = Color(0xFF050508)
private val Violet = Color(0xFFA855F7)
private val Cyan = Color(0xFF22D3EE)

private val GlassSurface = Color(0xFF0D0D14)
private val GlassBorder = Color(0xFF1E1E2E)
private val CodeBackground = Color(0xFF0A0A12)
private val TextSurface = Color(0xFF0E0E16)

private val MutedText = Color(0xFF7A7A94)
private val PrimaryText = Color(0xFFE8E8F0)
private val LineNumberColor = Color(0xFF3A3A50)

/**
 * In-app file viewer with glass-morphism aesthetic.
 * Renders different file types:
 * - .txt, .csv, .json -> plain monospace text
 * - .md -> rendered markdown
 * - .html -> simplified text rendering (HTML tags stripped for safety)
 * - images -> full-size image preview
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(
    file: File,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onAskAi: (() -> Unit)? = null,
) {
    val extension = file.extension.lowercase()
    val content = remember(file) {
        try {
            if (isTextFile(extension)) file.readText() else null
        } catch (_: Exception) { null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack),
    ) {
        // ── Glass Top App Bar ────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            GlassSurface.copy(alpha = 0.92f),
                            GlassSurface.copy(alpha = 0.75f),
                        ),
                    ),
                )
                .drawBehind {
                    // Bottom border glow line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Violet.copy(alpha = 0.25f),
                                Cyan.copy(alpha = 0.15f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back button with glass circle
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            color = Color.White.copy(alpha = 0.06f),
                            shape = CircleShape,
                        )
                        .border(
                            width = 0.5.dp,
                            color = GlassBorder.copy(alpha = 0.6f),
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PrimaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // File info: name + size
                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.2).sp,
                        ),
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = formatFileSize(file.length()),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                        ),
                        color = MutedText,
                    )
                }

                // Action buttons with glass circle backgrounds
                if (onAskAi != null) {
                    IconButton(
                        onClick = onAskAi,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                color = Violet.copy(alpha = 0.12f),
                                shape = CircleShape,
                            )
                            .border(
                                width = 0.5.dp,
                                color = Violet.copy(alpha = 0.20f),
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Ask AI",
                            tint = Violet,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            color = Cyan.copy(alpha = 0.10f),
                            shape = CircleShape,
                        )
                        .border(
                            width = 0.5.dp,
                            color = Cyan.copy(alpha = 0.18f),
                            shape = CircleShape,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Cyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // ── Content area ─────────────────────────────────────────────────────
        when {
            isImageFile(extension) -> {
                ImageViewer(file)
            }
            extension == "md" && content != null -> {
                MarkdownViewer(content)
            }
            extension == "html" || extension == "htm" -> {
                HtmlViewer(file, content ?: "Unable to read file")
            }
            content != null -> {
                PlainTextViewer(content, extension)
            }
            else -> {
                UnsupportedFile(file.name, extension)
            }
        }
    }
}

// ── Plain Text Viewer ────────────────────────────────────────────────────────

@Composable
private fun PlainTextViewer(content: String, extension: String) {
    val isCode = extension in listOf("json", "csv", "xml", "yaml", "yml")
    val lines = remember(content) { content.lines() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 0.5.dp,
                color = if (isCode) Violet.copy(alpha = 0.10f) else GlassBorder.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
            )
            .background(if (isCode) CodeBackground else TextSurface),
    ) {
        val scrollState = rememberScrollState()
        val hScrollState = rememberScrollState()

        if (isCode) {
            // Code view with line numbers
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                // Line number gutter
                Column(
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = 14.dp, start = 8.dp)
                        .width(36.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    lines.forEachIndexed { index, _ ->
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 18.sp,
                            ),
                            color = LineNumberColor,
                        )
                    }
                }

                // Subtle divider between gutter and code
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(Violet.copy(alpha = 0.08f)),
                )

                // Code content
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    ),
                    color = Cyan.copy(alpha = 0.85f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScrollState)
                        .padding(
                            start = 10.dp,
                            end = 14.dp,
                            top = 14.dp,
                            bottom = 14.dp,
                        ),
                )
            }
        } else {
            // Regular text view
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Default,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                ),
                color = PrimaryText,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .horizontalScroll(hScrollState)
                    .padding(16.dp),
            )
        }
    }
}

// ── Markdown Viewer ──────────────────────────────────────────────────────────

@Composable
private fun MarkdownViewer(content: String) {
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 0.5.dp,
                color = GlassBorder.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GlassSurface.copy(alpha = 0.85f),
                        Color(0xFF0B0B14).copy(alpha = 0.9f),
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(18.dp),
        ) {
            MarkdownText(
                markdown = content,
                color = PrimaryText,
            )
        }
    }
}

// ── HTML Viewer ──────────────────────────────────────────────────────────────

@Composable
private fun HtmlViewer(file: File, content: String) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Open in Browser button with violet-to-cyan gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 0.5.dp,
                    color = GlassBorder.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                )
                .background(GlassSurface.copy(alpha = 0.7f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Gradient button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Violet, Cyan),
                            ),
                        )
                        .clickable {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file,
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "text/html")
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_ACTIVITY_NEW_TASK,
                                    )
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                // If no browser, fall through to in-app preview
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Open in Browser",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = Color.White,
                            )
                            Text(
                                text = "View with full HTML rendering",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 11.sp,
                                ),
                                color = Color.White.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }

        // In-app WebView preview with dark background
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = false  // Security: no JS in preview
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(android.graphics.Color.parseColor("#050508"))
                    loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = 0.5.dp,
                    color = GlassBorder.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp),
                ),
        )
    }
}

// ── Image Viewer ─────────────────────────────────────────────────────────────

@Composable
private fun ImageViewer(file: File) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrueBlack)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Uri.fromFile(file),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(16.dp),
                    ambientColor = Violet.copy(alpha = 0.15f),
                    spotColor = Violet.copy(alpha = 0.10f),
                )
                .clip(RoundedCornerShape(16.dp)),
        )
    }
}

// ── Unsupported File ─────────────────────────────────────────────────────────

@Composable
private fun UnsupportedFile(name: String, extension: String) {
    // Animated floating icon
    val infiniteTransition = rememberInfiniteTransition(label = "unsupported_anim")
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float_y",
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow_alpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Glass card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = 0.5.dp,
                    color = GlassBorder.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp),
                )
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            GlassSurface.copy(alpha = 0.85f),
                            Color(0xFF0B0B14).copy(alpha = 0.9f),
                        ),
                    ),
                )
                .padding(horizontal = 28.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Animated file icon
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationY = floatOffset
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Glow behind icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Violet.copy(alpha = glowAlpha),
                                    Color.Transparent,
                                ),
                            ),
                            shape = CircleShape,
                        ),
                )
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Violet.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Cannot preview",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = PrimaryText,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = ".$extension files are not supported for in-app preview.",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // Suggestion with subtle highlight
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cyan.copy(alpha = 0.06f))
                    .border(
                        width = 0.5.dp,
                        color = Cyan.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Cyan.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Use the share button to open in another app",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cyan.copy(alpha = 0.7f),
                )
            }
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun isTextFile(ext: String) = ext in listOf(
    "txt", "md", "html", "htm", "json", "csv", "xml",
    "yaml", "yml", "log", "ini", "cfg", "properties",
)

private fun isImageFile(ext: String) = ext in listOf(
    "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp",
)

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}
