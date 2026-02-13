package com.openclaw.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.openclaw.android.ui.components.MarkdownText
import java.io.File

/**
 * In-app file viewer. Renders different file types:
 * - .txt, .csv, .json → plain monospace text
 * - .md → rendered markdown
 * - .html → simplified text rendering (HTML tags stripped for safety)
 * - images → full-size image preview
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

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    Text(
                        text = formatFileSize(file.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            },
            actions = {
                if (onAskAi != null) {
                    IconButton(onClick = onAskAi) {
                        Icon(Icons.Default.AutoAwesome, "Ask AI")
                    }
                }
                IconButton(onClick = onShare) {
                    Icon(Icons.Default.Share, "Share")
                }
            },
        )

        // Content area
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

@Composable
private fun PlainTextViewer(content: String, extension: String) {
    val isCode = extension in listOf("json", "csv", "xml", "yaml", "yml")

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isCode) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surface,
    ) {
        val scrollState = rememberScrollState()
        val hScrollState = rememberScrollState()

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (isCode) FontFamily.Monospace else FontFamily.Default,
                fontSize = if (isCode) 13.sp else 15.sp,
                lineHeight = if (isCode) 18.sp else 22.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .horizontalScroll(hScrollState)
                .padding(16.dp),
        )
    }
}

@Composable
private fun MarkdownViewer(content: String) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            MarkdownText(
                markdown = content,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun HtmlViewer(file: File, content: String) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // Open in browser button
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier
                    .clickable {
                        try {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file,
                            )
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, "text/html")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            // If no browser, fall through to in-app preview
                        }
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.OpenInBrowser, null, Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Open in Browser", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("View with full HTML rendering", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                }
            }
        }

        // In-app WebView preview
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = false  // Security: no JS in preview
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun ImageViewer(file: File) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = Uri.fromFile(file),
            contentDescription = file.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun UnsupportedFile(name: String, extension: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Cannot preview",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = ".$extension files are not supported for in-app preview.\nUse the share button to open in another app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

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
