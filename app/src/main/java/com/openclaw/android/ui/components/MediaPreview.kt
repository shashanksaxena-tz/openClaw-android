package com.openclaw.android.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

// Design system colors (ElectricViolet, NeonCyan from ClayCard.kt)
private val RemoveGradientStart = Color(0xFFEF4444)
private val RemoveGradientEnd = Color(0xFFDC2626)
private val GlassBorder = Color.White.copy(alpha = 0.08f)
private val DarkGlass = Color(0xFF1A1A2E).copy(alpha = 0.85f)

private val ThumbnailShape = RoundedCornerShape(14.dp)

/**
 * Shows a horizontal strip of attached media previews with glass morphism design.
 * Used in both the chat input area and inside message bubbles.
 * Premium dark-first aesthetic with animated interactions.
 */
@Composable
fun MediaPreviewStrip(
    media: List<MediaItem>,
    onRemove: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (media.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
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
        modifier = modifier.size(72.dp),
    ) {
        // Main thumbnail content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(ThumbnailShape)
                .border(
                    width = 0.5.dp,
                    color = GlassBorder,
                    shape = ThumbnailShape,
                ),
        ) {
            when (item) {
                is MediaItem.Image -> {
                    AsyncImage(
                        model = item.uri,
                        contentDescription = "Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(ThumbnailShape),
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
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(ThumbnailShape),
                        )
                        // Glass overlay play button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(
                                    width = 0.5.dp,
                                    color = Color.White.copy(alpha = 0.15f),
                                    shape = CircleShape,
                                ),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Video",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                is MediaItem.Audio -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        ElectricViolet.copy(alpha = 0.7f),
                                        NeonCyan.copy(alpha = 0.5f),
                                    ),
                                ),
                            ),
                    ) {
                        Icon(
                            Icons.Default.AudioFile,
                            contentDescription = "Audio",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                is MediaItem.File -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(DarkGlass)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            fileTypeIcon(item.name),
                            contentDescription = "File",
                            tint = ElectricViolet,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // Remove button - small gradient circle at top-right with animated scale
        if (onRemove != null) {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ),
                exit = scaleOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-6).dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(RemoveGradientStart, RemoveGradientEnd),
                            ),
                        )
                        .clickable(onClick = onRemove),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                }
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
