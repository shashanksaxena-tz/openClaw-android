package com.openclaw.android.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.openclaw.android.sandbox.SandboxedFileSystem
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    fs: SandboxedFileSystem,
    modifier: Modifier = Modifier,
    activeSpaceName: String? = null,
    onAskAi: ((File) -> Unit)? = null,
) {
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf("") }
    var isShowingShared by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<SandboxedFileSystem.FileInfo>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var fileToDelete by remember { mutableStateOf<SandboxedFileSystem.FileInfo?>(null) }
    var fileToView by remember { mutableStateOf<File?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Auto-refresh when space changes
    LaunchedEffect(activeSpaceName) {
        currentPath = ""
        refreshTrigger++
    }

    LaunchedEffect(currentPath, isShowingShared, refreshTrigger) {
        isRefreshing = true
        files = if (isShowingShared) {
            fs.listShared()
        } else {
            fs.listWorkspace(currentPath).getOrDefault(emptyList())
        }
        isRefreshing = false
    }

    val usage = remember(refreshTrigger) { fs.getUsage() }

    // File viewer overlay
    fileToView?.let { file ->
        FileViewerScreen(
            file = file,
            onBack = { fileToView = null },
            onShare = { shareResolvedFile(context, file); fileToView = null },
            onAskAi = if (onAskAi != null) { { onAskAi(file); fileToView = null } } else null,
        )
        return
    }

    // Delete confirmation
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${file.name}?") },
            text = {
                Text(if (file.isDirectory) "This will delete the folder and all its contents."
                else "This file will be permanently deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = if (isShowingShared) fs.resolveShared(file.path).getOrNull()
                        else fs.resolve(file.path).getOrNull()
                        target?.let { if (it.isDirectory) it.deleteRecursively() else it.delete() }
                        fileToDelete = null
                        refreshTrigger++
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { fileToDelete = null }) { Text("Cancel") } },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header with space awareness
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentPath.isNotEmpty()) {
                IconButton(onClick = { currentPath = currentPath.substringBeforeLast("/", "") }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        isShowingShared -> "Shared Media"
                        activeSpaceName != null -> "$activeSpaceName Files"
                        else -> "Workspace"
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
                if (currentPath.isNotEmpty()) {
                    Text(currentPath, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else if (activeSpaceName != null && !isShowingShared) {
                    Text("Files in active space", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            IconButton(onClick = { refreshTrigger++ }) {
                Icon(Icons.Default.Refresh, "Refresh")
            }
        }

        // Filter chips
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
            FilterChip(
                selected = !isShowingShared,
                onClick = { isShowingShared = false; currentPath = "" },
                label = { Text("Workspace") },
                leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(18.dp)) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = isShowingShared,
                onClick = { isShowingShared = true; currentPath = "" },
                label = { Text("Shared") },
                leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(18.dp)) },
            )
            Spacer(Modifier.weight(1f))
            Text("${usage.fileCount} files, ${usage.displaySize}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically))
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        if (files.isEmpty() && !isRefreshing) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isShowingShared) Icons.Default.Share else Icons.Default.CreateNewFolder,
                        null, Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (isShowingShared) "No shared media yet.\nShare images, audio, or files from other apps."
                        else "Workspace is empty.\nThe AI assistant will create files here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshTrigger++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(modifier = Modifier.animateContentSize()) {
                    items(files, key = { it.name }) { file ->
                        FileRow(
                            file = file,
                            onClick = {
                                if (file.isDirectory && !isShowingShared) {
                                    currentPath = if (currentPath.isEmpty()) file.name else "$currentPath/${file.name}"
                                } else if (!file.isDirectory) {
                                    val resolved = if (isShowingShared) fs.resolveShared(file.path).getOrNull()
                                    else fs.resolve(file.path).getOrNull()
                                    if (resolved != null && resolved.exists()) fileToView = resolved
                                }
                            },
                            onDelete = { fileToDelete = file },
                            onShare = {
                                val resolved = if (isShowingShared) fs.resolveShared(file.path).getOrNull()
                                else fs.resolve(file.path).getOrNull()
                                if (resolved != null) shareResolvedFile(context, resolved)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: SandboxedFileSystem.FileInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else fileIcon(file.name),
            contentDescription = null,
            tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(file.name, style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = buildString {
                    if (!file.isDirectory) { append(formatSize(file.size)); append(" · ") }
                    append(dateFormat.format(Date(file.lastModified)))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (!file.isDirectory) {
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Share, "Share", Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, "Delete", Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
    }
}

private fun shareResolvedFile(context: Context, file: File) {
    if (!file.exists() || file.isDirectory) return
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = getMimeTypeForFile(file.extension)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) { }
}

private fun getMimeTypeForFile(extension: String): String = when (extension.lowercase()) {
    "txt" -> "text/plain"; "md" -> "text/markdown"; "html", "htm" -> "text/html"; "csv" -> "text/csv"
    "json" -> "application/json"; "pdf" -> "application/pdf"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"
    "gif" -> "image/gif"; "mp4" -> "video/mp4"; "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"
    else -> "application/octet-stream"
}

private fun fileIcon(name: String) = when (name.substringAfterLast('.').lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "heic" -> Icons.Default.Image
    "mp4", "mkv", "avi", "mov" -> Icons.Default.Videocam
    "mp3", "wav", "ogg", "m4a" -> Icons.Default.MusicNote
    "pdf" -> Icons.Default.PictureAsPdf
    "txt", "md", "json", "csv" -> Icons.Default.Description
    "html", "htm" -> Icons.Default.Language
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}
