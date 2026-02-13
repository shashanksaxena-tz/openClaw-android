package com.openclaw.android.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.openclaw.android.sandbox.SandboxedFileSystem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    fs: SandboxedFileSystem,
    onBack: () -> Unit,
) {
    var currentPath by remember { mutableStateOf("") }
    var isShowingShared by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf<List<SandboxedFileSystem.FileInfo>>(emptyList()) }

    // Load files
    LaunchedEffect(currentPath, isShowingShared) {
        files = if (isShowingShared) {
            fs.listShared()
        } else {
            fs.listWorkspace(currentPath).getOrDefault(emptyList())
        }
    }

    val usage = remember { fs.getUsage() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (isShowingShared) "Shared Media" else "Workspace")
                        if (currentPath.isNotEmpty()) {
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            currentPath.isNotEmpty() -> {
                                currentPath = currentPath.substringBeforeLast("/", "")
                            }
                            isShowingShared -> {
                                isShowingShared = false
                            }
                            else -> onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Tab bar: Workspace / Shared
            if (currentPath.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    FilterChip(
                        selected = !isShowingShared,
                        onClick = { isShowingShared = false },
                        label = { Text("Workspace") },
                        leadingIcon = {
                            Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = isShowingShared,
                        onClick = { isShowingShared = true },
                        label = { Text("Shared") },
                        leadingIcon = {
                            Icon(Icons.Default.Share, null, Modifier.size(18.dp))
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "${usage.fileCount} files, ${usage.displaySize}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (files.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (isShowingShared)
                            "No shared media yet.\nShare images, audio, or files from other apps."
                        else
                            "Workspace is empty.\nThe AI assistant will create files here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(files) { file ->
                        FileRow(
                            file = file,
                            onClick = {
                                if (file.isDirectory && !isShowingShared) {
                                    currentPath = if (currentPath.isEmpty()) file.name else "$currentPath/${file.name}"
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: SandboxedFileSystem.FileInfo,
    onClick: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder
            else fileIcon(file.name),
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
            )
            Text(
                text = buildString {
                    if (!file.isDirectory) {
                        append(formatSize(file.size))
                        append(" - ")
                    }
                    append(dateFormat.format(Date(file.lastModified)))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun fileIcon(name: String) = when (name.substringAfterLast('.').lowercase()) {
    "jpg", "jpeg", "png", "gif", "webp", "heic" -> Icons.Default.Image
    "mp4", "mkv", "avi", "mov" -> Icons.Default.Videocam
    "mp3", "wav", "ogg", "m4a" -> Icons.Default.MusicNote
    "pdf" -> Icons.Default.PictureAsPdf
    "txt", "md", "json", "csv" -> Icons.Default.Description
    else -> Icons.AutoMirrored.Filled.InsertDriveFile
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes.toDouble() / (1024 * 1024))} MB"
}
