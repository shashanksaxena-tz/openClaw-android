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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.openclaw.android.sandbox.SandboxedFileSystem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileBrowserScreen(
    fs: SandboxedFileSystem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var currentPath by remember { mutableStateOf("") }
    var isShowingShared by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<SandboxedFileSystem.FileInfo>>(emptyList()) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var fileToDelete by remember { mutableStateOf<SandboxedFileSystem.FileInfo?>(null) }

    LaunchedEffect(currentPath, isShowingShared, refreshTrigger) {
        files = if (isShowingShared) {
            fs.listShared()
        } else {
            fs.listWorkspace(currentPath).getOrDefault(emptyList())
        }
    }

    val usage = remember(refreshTrigger) { fs.getUsage() }

    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete ${file.name}?") },
            text = {
                Text(
                    if (file.isDirectory) "This will delete the folder and all its contents."
                    else "This file will be permanently deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = if (isShowingShared) {
                            fs.resolveShared(file.path).getOrNull()
                        } else {
                            fs.resolve(file.path).getOrNull()
                        }
                        target?.let {
                            if (it.isDirectory) it.deleteRecursively() else it.delete()
                        }
                        fileToDelete = null
                        refreshTrigger++
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Cancel") }
            },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentPath.isNotEmpty()) {
                IconButton(onClick = {
                    currentPath = currentPath.substringBeforeLast("/", "")
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isShowingShared) "Shared Media" else "Workspace",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (currentPath.isNotEmpty()) {
                    Text(
                        text = currentPath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = { refreshTrigger++ }) {
                Icon(Icons.Default.Refresh, "Refresh")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            FilterChip(
                selected = !isShowingShared,
                onClick = {
                    isShowingShared = false
                    currentPath = ""
                },
                label = { Text("Workspace") },
                leadingIcon = { Icon(Icons.Default.Folder, null, Modifier.size(18.dp)) },
            )
            Spacer(Modifier.width(8.dp))
            FilterChip(
                selected = isShowingShared,
                onClick = {
                    isShowingShared = true
                    currentPath = ""
                },
                label = { Text("Shared") },
                leadingIcon = { Icon(Icons.Default.Share, null, Modifier.size(18.dp)) },
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${usage.fileCount} files, ${usage.displaySize}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        if (files.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (isShowingShared) Icons.Default.Share else Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (isShowingShared)
                            "No shared media yet.\nShare images, audio, or files from other apps."
                        else
                            "Workspace is empty.\nThe AI assistant will create files here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.animateContentSize()) {
                items(files, key = { it.name }) { file ->
                    FileRow(
                        file = file,
                        onClick = {
                            if (file.isDirectory && !isShowingShared) {
                                currentPath = if (currentPath.isEmpty()) file.name
                                    else "$currentPath/${file.name}"
                            } else if (!file.isDirectory) {
                                shareFile(context, fs, file, isShowingShared)
                            }
                        },
                        onDelete = { fileToDelete = file },
                        onShare = { shareFile(context, fs, file, isShowingShared) },
                    )
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
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (!file.isDirectory) {
                        append(formatSize(file.size))
                        append(" · ")
                    }
                    append(dateFormat.format(Date(file.lastModified)))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!file.isDirectory) {
            IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        }
    }
}

private fun shareFile(context: Context, fs: SandboxedFileSystem, file: SandboxedFileSystem.FileInfo, isShared: Boolean) {
    val resolvedFile = if (isShared) fs.resolveShared(file.path).getOrNull() else fs.resolve(file.path).getOrNull() ?: return
    if (resolvedFile == null || !resolvedFile.exists() || resolvedFile.isDirectory) return
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", resolvedFile)
        val mimeType = getMimeTypeForFile(resolvedFile.extension)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
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
