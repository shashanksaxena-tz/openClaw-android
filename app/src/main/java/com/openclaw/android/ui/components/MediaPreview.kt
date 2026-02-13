package com.openclaw.android.ui.components

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * Shows a horizontal strip of attached media previews (images, video thumbnails, files).
 * Used in both the chat input area and inside message bubbles.
 */
@Composable
fun MediaPreviewStrip(
    media: List<MediaItem>,
    onRemove: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (media.isEmpty()) return

    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        items(media.size) { index ->
            val item = media[index]
            MediaThumbnail(
                item = item,
                onRemove = onRemove?.let { { it(index) } },
            )
        }
    }
}

@Composable
fun MediaThumbnail(
    item: MediaItem,
    onRemove: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        when (item) {
            is MediaItem.Image -> {
                AsyncImage(
                    model = item.uri,
                    contentDescription = "Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            is MediaItem.Video -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = "Video",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Icon(
                        Icons.Default.PlayCircleFilled,
                        contentDescription = "Video",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            is MediaItem.Audio -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Icon(
                        Icons.Default.AudioFile,
                        contentDescription = "Audio",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            is MediaItem.File -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        fileTypeIcon(item.name),
                        contentDescription = "File",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.name.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
        }

        // Remove button
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

sealed class MediaItem {
    data class Image(val uri: Uri, val name: String = "") : MediaItem()
    data class Video(val uri: Uri, val name: String = "") : MediaItem()
    data class Audio(val uri: Uri, val name: String = "") : MediaItem()
    data class File(val uri: Uri, val name: String, val mimeType: String = "") : MediaItem()
}

private fun fileTypeIcon(name: String): ImageVector = when (name.substringAfterLast('.').lowercase()) {
    "pdf" -> Icons.Default.PictureAsPdf
    "doc", "docx" -> Icons.Default.Description
    "txt", "md" -> Icons.Default.Article
    else -> Icons.Default.InsertDriveFile
}
