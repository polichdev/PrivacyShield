package com.example.privacyshield.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.privacyshield.R
import com.example.privacyshield.ui.theme.OrangePrimary
import com.example.privacyshield.youtube.OfflineDownloadManager
import com.example.privacyshield.youtube.QueueItem
import com.example.privacyshield.youtube.YouTubeAudioService
import com.example.privacyshield.youtube.YouTubePlayerState
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val videoId by YouTubePlayerState.currentVideoId.collectAsStateWithLifecycle()
    val title by YouTubePlayerState.currentTitle.collectAsStateWithLifecycle()
    val author by YouTubePlayerState.currentAuthor.collectAsStateWithLifecycle()
    val artwork by YouTubePlayerState.thumbnailBitmap.collectAsStateWithLifecycle()
    val isPlaying by YouTubePlayerState.isPlaying.collectAsStateWithLifecycle()
    val isLoading by YouTubePlayerState.isLoading.collectAsStateWithLifecycle()
    val playlist by YouTubePlayerState.playlist.collectAsStateWithLifecycle()

    val downloadingIds by OfflineDownloadManager.downloadingIds.collectAsStateWithLifecycle()
    val downloadedIds by OfflineDownloadManager.downloadedIds.collectAsStateWithLifecycle()

    val active = videoId != null
    val isCurrentDownloading = videoId != null && downloadingIds.contains(videoId)
    val isCurrentDownloaded = videoId != null && downloadedIds.contains(videoId)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillParentMaxHeight()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 290.dp)
                        .aspectRatio(1f)
                        .shadow(elevation = 16.dp, shape = RoundedCornerShape(24.dp), spotColor = Color(0x66000000))
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = artwork
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.player_art),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        EmptyPlayerArtwork()
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = OrangePrimary,
                                strokeWidth = 3.5.dp,
                                modifier = Modifier.size(46.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = title ?: stringResource(R.string.player_empty),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = author ?: stringResource(R.string.player_share_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                PlaybackSlider(active = active)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Surface(
                        onClick = { YouTubePlayerState.openInYouTube(context) },
                        enabled = active,
                        color = if (active) Color(0xFFFF0000) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_youtube),
                                contentDescription = stringResource(R.string.player_open),
                                tint = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        onClick = {
                            val id = videoId
                            if (id != null) {
                                if (isCurrentDownloaded) {
                                    OfflineDownloadManager.delete(context, id)
                                    Toast.makeText(context, context.getString(R.string.player_toast_deleted), Toast.LENGTH_SHORT).show()
                                } else if (!isCurrentDownloading) {
                                    coroutineScope.launch {
                                        Toast.makeText(context, context.getString(R.string.player_toast_download_started), Toast.LENGTH_SHORT).show()
                                        val ok = OfflineDownloadManager.download(context, id)
                                        if (ok) {
                                            Toast.makeText(context, context.getString(R.string.player_toast_download_saved), Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, context.getString(R.string.player_toast_download_failed), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        },
                        enabled = active,
                        color = if (isCurrentDownloaded) Color(0xFF1DB954) else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isCurrentDownloading) {
                                CircularProgressIndicator(
                                    color = OrangePrimary,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(if (isCurrentDownloaded) R.drawable.ic_check else R.drawable.ic_download),
                                    contentDescription = if (isCurrentDownloaded) "Downloaded" else "Download Offline",
                                    tint = if (isCurrentDownloaded) Color.White else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        onClick = { YouTubeAudioService.stop(context) },
                        enabled = active,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.player_stop),
                                tint = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Surface(
                        onClick = {
                            val prev = YouTubePlayerState.getPreviousQueueItem(videoId)
                            if (prev != null) {
                                val seekSec = (prev.lastPositionMs / 1000L).coerceAtLeast(0L)
                                YouTubeAudioService.start(context, prev.videoId, seekSec)
                            }
                        },
                        enabled = active && playlist.size > 1,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_previous),
                                contentDescription = "Попереднє",
                                tint = if (active && playlist.size > 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { YouTubeAudioService.replay10(context) },
                        enabled = active,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_replay_10),
                                contentDescription = stringResource(R.string.player_replay),
                                tint = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { YouTubeAudioService.toggle(context) },
                        enabled = active || playlist.isNotEmpty(),
                        shape = CircleShape,
                        color = if (active || playlist.isNotEmpty()) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant,
                        shadowElevation = if (active) 10.dp else 0.dp,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(32.dp)
                                )
                            } else {
                                Icon(
                                    painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                                    contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                                    tint = if (active || playlist.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { YouTubeAudioService.forward10(context) },
                        enabled = active,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_forward_10),
                                contentDescription = stringResource(R.string.player_forward),
                                tint = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            val next = YouTubePlayerState.getNextQueueItem(videoId)
                            if (next != null) {
                                val seekSec = (next.lastPositionMs / 1000L).coerceAtLeast(0L)
                                YouTubeAudioService.start(context, next.videoId, seekSec)
                            }
                        },
                        enabled = active && playlist.size > 1,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_next),
                                contentDescription = "Наступне",
                                tint = if (active && playlist.size > 1) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.player_queue, playlist.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (playlist.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.player_clear),
                        style = MaterialTheme.typography.bodySmall,
                        color = OrangePrimary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                playlist.forEach { OfflineDownloadManager.delete(context, it.videoId) }
                                YouTubePlayerState.clearPlaylist()
                            }
                            .padding(4.dp)
                    )
                }
            }
        }

        if (playlist.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = stringResource(R.string.player_queue_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(playlist, key = { it.videoId }) { item ->
                QueueItemRow(
                    item = item,
                    isDownloaded = downloadedIds.contains(item.videoId),
                    isDownloading = downloadingIds.contains(item.videoId),
                    onPlay = {
                        if (item.videoId == videoId) {
                            YouTubeAudioService.toggle(context)
                        } else {
                            val seekSec = (item.lastPositionMs / 1000L).coerceAtLeast(0L)
                            YouTubeAudioService.start(context, item.videoId, seekSec)
                        }
                    },
                    onDownload = {
                        coroutineScope.launch {
                            Toast.makeText(context, context.getString(R.string.player_toast_downloading), Toast.LENGTH_SHORT).show()
                            val ok = OfflineDownloadManager.download(context, item.videoId)
                            if (ok) {
                                Toast.makeText(context, context.getString(R.string.player_toast_saved_offline), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDelete = {
                        OfflineDownloadManager.delete(context, item.videoId)
                        val wasCurrent = item.videoId == videoId
                        YouTubePlayerState.removeFromPlaylist(item.videoId)
                        if (wasCurrent) {
                            val next = YouTubePlayerState.getNextQueueItem(videoId)
                            if (next != null) {
                                YouTubeAudioService.start(context, next.videoId, 0L)
                            } else {
                                YouTubeAudioService.stop(context)
                            }
                        }
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun QueueItemRow(
    item: QueueItem,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbnailBitmap by YouTubePlayerState.getThumbnailFlow(item.videoId).collectAsStateWithLifecycle()
    val hasProgress = item.lastPositionMs > 0 && item.durationMs > 0
    val progressRatio = if (hasProgress) {
        (item.lastPositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Surface(
        onClick = onPlay,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (item.isPlaying) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, if (item.isPlaying) OrangePrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumbnailBitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_nav_player),
                        contentDescription = null,
                        tint = if (item.isPlaying) OrangePrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (item.isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (item.isPlaying) OrangePrimary else MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isDownloaded) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = "Offline",
                            tint = Color(0xFF1DB954),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (hasProgress) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(3.dp)
                            .clip(CircleShape),
                        color = OrangePrimary,
                        trackColor = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${YouTubePlayerState.formatTime(item.lastPositionMs)} / ${YouTubePlayerState.formatTime(item.durationMs)}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isDownloaded) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(34.dp),
                    enabled = !isDownloading
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            color = OrangePrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_download),
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(if (item.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                    contentDescription = stringResource(R.string.player_play_track),
                    tint = if (item.isPlaying) OrangePrimary else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.player_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyPlayerArtwork() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Canvas(modifier = Modifier.size(110.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            drawCircle(
                color = Color(0x33888899),
                radius = radius,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color(0x44888899),
                radius = radius * 0.75f,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = Color(0x55888899),
                radius = radius * 0.5f,
                style = Stroke(width = 1.5.dp.toPx())
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFFFF7D1A), Color(0xFFFF6A00)),
                    center = center,
                    radius = radius * 0.26f
                ),
                radius = radius * 0.26f
            )

            drawCircle(
                color = Color(0xFF0F0F12),
                radius = radius * 0.08f
            )

            val waveBarWidth = 2.dp.toPx()
            val startX = center.x + radius * 0.45f
            drawLine(
                color = Color(0xFFFF6A00).copy(alpha = 0.8f),
                start = Offset(startX, center.y - 14.dp.toPx()),
                end = Offset(startX, center.y + 14.dp.toPx()),
                strokeWidth = waveBarWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFF6A00).copy(alpha = 0.5f),
                start = Offset(startX + 8.dp.toPx(), center.y - 22.dp.toPx()),
                end = Offset(startX + 8.dp.toPx(), center.y + 22.dp.toPx()),
                strokeWidth = waveBarWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFF6A00).copy(alpha = 0.3f),
                start = Offset(startX + 16.dp.toPx(), center.y - 10.dp.toPx()),
                end = Offset(startX + 16.dp.toPx(), center.y + 10.dp.toPx()),
                strokeWidth = waveBarWidth,
                cap = StrokeCap.Round
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.player_share_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(R.string.player_share_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun PlaybackSlider(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val positionMs by YouTubePlayerState.currentPositionMs.collectAsStateWithLifecycle()
    val durationMs by YouTubePlayerState.durationMs.collectAsStateWithLifecycle()

    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    val maxDuration = durationMs.coerceAtLeast(1L).toFloat()
    val sliderVal = if (isDragging) {
        dragPositionMs.coerceIn(0f, maxDuration)
    } else {
        positionMs.toFloat().coerceIn(0f, maxDuration)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = sliderVal,
            valueRange = 0f..maxDuration,
            onValueChange = {
                isDragging = true
                dragPositionMs = it
            },
            onValueChangeFinished = {
                isDragging = false
                YouTubeAudioService.seek(context, dragPositionMs.toLong())
            },
            enabled = active && durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = OrangePrimary,
                activeTrackColor = OrangePrimary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val currentFormatted = if (isDragging) {
                YouTubePlayerState.formatTime(dragPositionMs.toLong())
            } else {
                YouTubePlayerState.formatTime(positionMs)
            }
            Text(
                text = currentFormatted,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = YouTubePlayerState.formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

