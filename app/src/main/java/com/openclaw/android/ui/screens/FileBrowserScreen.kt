package com.openclaw.android.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.openclaw.android.sandbox.SandboxedFileSystem
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

// ── Design tokens (theme-aware) ─────────────────────────────────────────────
// Accent colors are shared across themes
private val AccentViolet = Color(0xFFA855F7)
private val AccentCyan = Color(0xFF22D3EE)
private val AccentPink = Color(0xFFF472B6)
private val DangerRed = Color(0xFFEF4444)
private val GlassShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(12.dp)

/** Theme-aware color provider — reads from MaterialTheme inside @Composable scope. */
private object FileBrowserColors {
    val background: Color @Composable get() = MaterialTheme.colorScheme.background
    val surface: Color @Composable get() = MaterialTheme.colorScheme.surface
    val surfaceVariant: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
    val surfaceContainer: Color @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
    val border: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
    val borderSubtle: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textMuted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
    val textSubtle: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    val primary: Color @Composable get() = MaterialTheme.colorScheme.primary
    val onSurface: Color @Composable get() = MaterialTheme.colorScheme.onSurface
}

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

    // ── Delete confirmation dialog ───────────────────────────────────────────
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            shape = GlassShape,
            containerColor = FileBrowserColors.surface,
            tonalElevation = 0.dp,
            modifier = Modifier
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(DangerRed.copy(alpha = 0.40f), DangerRed.copy(alpha = 0.10f))
                    ),
                    shape = GlassShape,
                )
                .shadow(
                    elevation = 24.dp,
                    shape = GlassShape,
                    ambientColor = DangerRed.copy(alpha = 0.25f),
                    spotColor = DangerRed.copy(alpha = 0.25f),
                ),
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(DangerRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Delete, null,
                        tint = DangerRed,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
            title = {
                Text(
                    "Delete ${file.name}?",
                    color = FileBrowserColors.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    if (file.isDirectory) "This will delete the folder and all its contents."
                    else "This file will be permanently deleted.",
                    color = FileBrowserColors.textSubtle,
                )
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
                    colors = ButtonDefaults.textButtonColors(contentColor = DangerRed),
                ) { Text("Delete", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel", color = FileBrowserColors.textSubtle)
                }
            },
        )
    }

    // ── Main layout ──────────────────────────────────────────────────────────
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FileBrowserColors.background),
    ) {
        // ── Header ───────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(FileBrowserColors.surfaceContainer)
                .drawBehind {
                    // Subtle gradient line at bottom
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                AccentViolet.copy(alpha = 0.5f),
                                AccentCyan.copy(alpha = 0.3f),
                                Color.Transparent,
                            )
                        ),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1f,
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Back button
                AnimatedVisibility(
                    visible = currentPath.isNotEmpty(),
                    enter = fadeIn(tween(200)) + scaleIn(tween(200)),
                    exit = fadeOut(tween(200)) + scaleOut(tween(200)),
                ) {
                    GlassCircleButton(
                        onClick = { currentPath = currentPath.substringBeforeLast("/", "") },
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = FileBrowserColors.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                // Title section
                Column(modifier = Modifier.weight(1f)) {
                    val titleText = when {
                        isShowingShared -> "Shared Media"
                        activeSpaceName != null -> "$activeSpaceName Files"
                        else -> "Workspace"
                    }
                    Text(
                        text = titleText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        style = LocalTextStyle.current.copy(
                            brush = Brush.horizontalGradient(listOf(AccentViolet, AccentCyan)),
                        ),
                    )

                    // Breadcrumb path or space subtitle
                    AnimatedContent(
                        targetState = currentPath,
                        transitionSpec = {
                            (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 3 })
                                .togetherWith(fadeOut(tween(150)) + slideOutHorizontally(tween(150)) { -it / 3 })
                        },
                        label = "breadcrumb",
                    ) { path ->
                        if (path.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Folder, null,
                                    tint = AccentViolet.copy(alpha = 0.5f),
                                    modifier = Modifier.size(12.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = path.replace("/", " / "),
                                    fontSize = 12.sp,
                                    color = FileBrowserColors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        } else if (activeSpaceName != null && !isShowingShared) {
                            Text(
                                "Files in active space",
                                fontSize = 12.sp,
                                color = FileBrowserColors.textMuted,
                            )
                        } else {
                            Spacer(Modifier.height(0.dp))
                        }
                    }
                }

                // Space chip
                if (activeSpaceName != null && !isShowingShared) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentViolet.copy(alpha = 0.10f))
                            .border(0.5.dp, AccentViolet.copy(alpha = 0.20f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            activeSpaceName,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = AccentViolet.copy(alpha = 0.8f),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }

                // Refresh button
                GlassCircleButton(onClick = { refreshTrigger++ }) {
                    Icon(
                        Icons.Default.Refresh, "Refresh",
                        tint = FileBrowserColors.onSurface.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        // ── Filter chips row ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassFilterChip(
                label = "Workspace",
                icon = Icons.Default.Folder,
                isSelected = !isShowingShared,
                onClick = { isShowingShared = false; currentPath = "" },
            )
            Spacer(Modifier.width(10.dp))
            GlassFilterChip(
                label = "Shared",
                icon = Icons.Default.Share,
                isSelected = isShowingShared,
                onClick = { isShowingShared = true; currentPath = "" },
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${usage.fileCount} files, ${usage.displaySize}",
                fontSize = 11.sp,
                color = FileBrowserColors.textMuted,
            )
        }

        // ── Gradient divider ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(0.5.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            AccentViolet.copy(alpha = 0.4f),
                            AccentCyan.copy(alpha = 0.15f),
                            Color.Transparent,
                        )
                    )
                ),
        )

        // ── Content area ─────────────────────────────────────────────────────
        if (files.isEmpty() && !isRefreshing) {
            // ── Empty state ──────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.7f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = EaseInOutCubic),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseAlpha",
                )
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 0.95f,
                    targetValue = 1.05f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = EaseInOutCubic),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseScale",
                )

                Box(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(GlassShape)
                        .background(FileBrowserColors.surfaceVariant)
                        .border(0.5.dp, FileBrowserColors.borderSubtle, GlassShape)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = if (isShowingShared) Icons.Default.Share else Icons.Default.CreateNewFolder,
                            contentDescription = null,
                            modifier = Modifier
                                .size(56.dp)
                                .graphicsLayer {
                                    alpha = pulseAlpha
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                },
                            tint = if (isShowingShared) AccentCyan.copy(alpha = 0.6f) else AccentViolet.copy(alpha = 0.6f),
                        )
                        Spacer(Modifier.height(20.dp))
                        Text(
                            text = if (isShowingShared) "No shared media yet"
                            else "Workspace is empty",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = FileBrowserColors.onSurface.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isShowingShared) "Share images, audio, or files\nfrom other apps."
                            else "The AI assistant will\ncreate files here.",
                            fontSize = 13.sp,
                            color = FileBrowserColors.textMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { refreshTrigger++ },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(files, key = { _, f -> f.name }) { index, file ->
                        GlassFileRow(
                            file = file,
                            animationDelay = index * 30,
                            onClick = {
                                if (file.isDirectory && !isShowingShared) {
                                    currentPath = if (currentPath.isEmpty()) file.name else "$currentPath/${file.name}"
                                } else if (!file.isDirectory) {
                                    val resolved = if (isShowingShared) fs.resolveShared(file.path).getOrNull()
                                    else fs.resolve(file.path).getOrNull()
                                    if (resolved != null && resolved.exists()) fileToView = resolved
                                }
                            },
                            onSaveToDownloads = {
                                val resolved = if (isShowingShared) fs.resolveShared(file.path).getOrNull()
                                else fs.resolve(file.path).getOrNull()
                                if (resolved != null && resolved.exists() && !resolved.isDirectory) {
                                    val success = saveToDownloads(context, resolved)
                                    Toast.makeText(
                                        context,
                                        if (success) "Saved to Downloads/OpenClaw/${file.name}"
                                        else "Failed to save",
                                        Toast.LENGTH_SHORT,
                                    ).show()
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
                    // Bottom padding to avoid content being cut off
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

// ── Glass circle button ──────────────────────────────────────────────────────
@Composable
private fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(FileBrowserColors.surfaceVariant.copy(alpha = 0.6f))
            .border(0.5.dp, FileBrowserColors.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── Glass filter chip ────────────────────────────────────────────────────────
@Composable
private fun GlassFilterChip(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val selectedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(250),
        label = "chipSelect",
    )

    Box(
        modifier = Modifier
            .clip(ChipShape)
            .then(
                if (isSelected) {
                    Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    AccentViolet.copy(alpha = 0.25f),
                                    AccentCyan.copy(alpha = 0.15f),
                                )
                            )
                        )
                        .border(
                            0.5.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    AccentViolet.copy(alpha = 0.5f),
                                    AccentCyan.copy(alpha = 0.3f),
                                )
                            ),
                            ChipShape,
                        )
                        .shadow(
                            elevation = 8.dp,
                            shape = ChipShape,
                            ambientColor = AccentViolet.copy(alpha = 0.3f),
                            spotColor = AccentViolet.copy(alpha = 0.3f),
                        )
                } else {
                    Modifier
                        .background(FileBrowserColors.surfaceVariant.copy(alpha = 0.4f))
                        .border(0.5.dp, FileBrowserColors.border, ChipShape)
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null,
                modifier = Modifier.size(16.dp),
                tint = if (isSelected) AccentViolet else FileBrowserColors.textMuted,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) FileBrowserColors.onSurface else FileBrowserColors.textSubtle,
            )
        }
    }
}

// ── Glass file row ───────────────────────────────────────────────────────────
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlassFileRow(
    file: SandboxedFileSystem.FileInfo,
    animationDelay: Int,
    onClick: () -> Unit,
    onSaveToDownloads: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(animationDelay.toLong())
        isVisible = true
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(300)) + slideInHorizontally(tween(300)) { it / 4 },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShape)
                .background(FileBrowserColors.surfaceVariant)
                .border(0.5.dp, FileBrowserColors.border, GlassShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onDelete,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // File icon with color coding
                val iconColor = fileIconColor(file)
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else fileIcon(file.name),
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(Modifier.width(12.dp))

                // File info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        file.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = FileBrowserColors.onSurface.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            if (!file.isDirectory) { append(formatSize(file.size)); append(" \u00B7 ") }
                            append(dateFormat.format(Date(file.lastModified)))
                        },
                        fontSize = 11.sp,
                        color = FileBrowserColors.textMuted,
                    )
                }

                // Action buttons
                if (!file.isDirectory) {
                    GlassCircleButton(onClick = onSaveToDownloads) {
                        Icon(
                            Icons.Default.Download, "Save to Downloads",
                            modifier = Modifier.size(15.dp),
                            tint = AccentCyan.copy(alpha = 0.8f),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    GlassCircleButton(onClick = onShare) {
                        Icon(
                            Icons.Default.Share, "Share",
                            modifier = Modifier.size(15.dp),
                            tint = FileBrowserColors.textSubtle,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                GlassCircleButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete, "Delete",
                        modifier = Modifier.size(15.dp),
                        tint = DangerRed.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ── Icon color coding ────────────────────────────────────────────────────────
private fun fileIconColor(file: SandboxedFileSystem.FileInfo): Color {
    if (file.isDirectory) return AccentViolet
    return when (file.name.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic" -> AccentCyan
        "mp4", "mkv", "avi", "mov" -> AccentPink
        "mp3", "wav", "ogg", "m4a" -> AccentPink
        "pdf", "txt", "md", "json", "csv", "html", "htm" -> FileBrowserColors.onSurface.copy(alpha = 0.75f)
        else -> FileBrowserColors.onSurface.copy(alpha = 0.55f)
    }
}

// ── Helper functions (preserved exactly) ─────────────────────────────────────

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

private fun saveToDownloads(context: Context, file: File): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, file.name)
                put(MediaStore.Downloads.MIME_TYPE, getMimeTypeForFile(file.extension))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/OpenClaw")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { os ->
                    file.inputStream().use { input -> input.copyTo(os) }
                }
                true
            } ?: false
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val openClawDir = File(downloadsDir, "OpenClaw").apply { mkdirs() }
            val dest = File(openClawDir, file.name)
            file.copyTo(dest, overwrite = true)
            true
        }
    } catch (_: Exception) {
        false
    }
}
