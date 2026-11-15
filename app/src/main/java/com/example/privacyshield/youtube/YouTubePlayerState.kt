package com.example.privacyshield.youtube

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URL
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class QueueItem(
    val videoId: String,
    val title: String,
    val author: String,
    val durationMs: Long = 0L,
    val lastPositionMs: Long = 0L,
    val isPlaying: Boolean = false
)

object YouTubePlayerState {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId: StateFlow<String?> = _currentVideoId.asStateFlow()

    private val _currentTitle = MutableStateFlow<String?>(null)
    val currentTitle: StateFlow<String?> = _currentTitle.asStateFlow()

    private val _currentAuthor = MutableStateFlow<String?>(null)
    val currentAuthor: StateFlow<String?> = _currentAuthor.asStateFlow()

    private val _thumbnailBitmap = MutableStateFlow<Bitmap?>(null)
    val thumbnailBitmap: StateFlow<Bitmap?> = _thumbnailBitmap.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _playlist = MutableStateFlow<List<QueueItem>>(emptyList())
    val playlist: StateFlow<List<QueueItem>> = _playlist.asStateFlow()

    private val thumbnailCache = LruCache<String, Bitmap>(50)
    private val thumbnailFlows = ConcurrentHashMap<String, MutableStateFlow<Bitmap?>>()

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun updateMetadata(videoId: String?, title: String?, author: String?, playing: Boolean) {
        _currentVideoId.value = videoId
        val isNewTitlePlaceholder = title != null && (title.startsWith("Завантаження") || title.startsWith("Loading"))
        val existingItem = _playlist.value.firstOrNull { it.videoId == videoId }
        val hasRealExistingTitle = existingItem != null && !existingItem.title.startsWith("Завантаження") && !existingItem.title.startsWith("Loading")

        val effectiveTitle = if (isNewTitlePlaceholder && hasRealExistingTitle) existingItem!!.title else title ?: _currentTitle.value
        val effectiveAuthor = if (author == "YouTube" && existingItem != null && existingItem.author != "YouTube") existingItem.author else author ?: _currentAuthor.value

        _currentTitle.value = effectiveTitle
        _currentAuthor.value = effectiveAuthor
        _isPlaying.value = playing
        _isLoading.value = false

        if (videoId != null) {
            loadThumbnail(videoId)

            _playlist.value = _playlist.value.map { item ->
                if (item.videoId == videoId) {
                    item.copy(
                        title = effectiveTitle ?: item.title,
                        author = effectiveAuthor ?: item.author,
                        isPlaying = playing
                    )
                } else {
                    item.copy(isPlaying = false)
                }
            }
        }
    }

    fun updateItemMetadata(videoId: String, title: String, author: String) {
        if (title.isBlank() || title.startsWith("Завантаження") || title.startsWith("Loading")) return
        _playlist.value = _playlist.value.map { item ->
            if (item.videoId == videoId) {
                item.copy(title = title, author = if (author != "YouTube" || item.author == "YouTube") author else item.author)
            } else item
        }
        if (_currentVideoId.value == videoId) {
            _currentTitle.value = title
            _currentAuthor.value = author
        }
    }

    fun updateProgress(positionMs: Long, durationMs: Long) {
        _currentPositionMs.value = positionMs
        _durationMs.value = durationMs

        val currentId = _currentVideoId.value ?: return
        _playlist.value = _playlist.value.map { item ->
            if (item.videoId == currentId) {
                item.copy(
                    lastPositionMs = positionMs,
                    durationMs = if (durationMs > 0) durationMs else item.durationMs,
                    isPlaying = _isPlaying.value
                )
            } else item
        }
    }

    fun addToPlaylist(item: QueueItem) {
        val existingIndex = _playlist.value.indexOfFirst { it.videoId == item.videoId }
        if (existingIndex == -1) {
            _playlist.value = _playlist.value + item
        } else {
            val existing = _playlist.value[existingIndex]
            val isOldPlaceholder = existing.title.isBlank() ||
                existing.title.startsWith("Завантаження") ||
                existing.title.startsWith("Loading")
            val isNewMeaningful = item.title.isNotBlank() &&
                !item.title.startsWith("Завантаження") &&
                !item.title.startsWith("Loading")

            if (isNewMeaningful || (isOldPlaceholder && item.title != existing.title)) {
                val updated = _playlist.value.toMutableList()
                updated[existingIndex] = existing.copy(
                    title = item.title,
                    author = if (item.author != "YouTube" || existing.author == "YouTube") item.author else existing.author
                )
                _playlist.value = updated
            }
        }
        preloadThumbnail(item.videoId)
    }

    fun removeFromPlaylist(videoId: String) {
        _playlist.value = _playlist.value.filter { it.videoId != videoId }
        thumbnailFlows.remove(videoId)
        thumbnailCache.remove(videoId)
    }

    fun getNextQueueItem(currentId: String?): QueueItem? {
        val list = _playlist.value
        if (list.isEmpty()) return null
        if (currentId == null) return list.firstOrNull()

        val currentIndex = list.indexOfFirst { it.videoId == currentId }
        return if (currentIndex in 0 until list.size - 1) {
            list[currentIndex + 1]
        } else {
            null
        }
    }

    fun getPreviousQueueItem(currentId: String?): QueueItem? {
        val list = _playlist.value
        if (list.isEmpty()) return null
        if (currentId == null) return list.firstOrNull()

        val currentIndex = list.indexOfFirst { it.videoId == currentId }
        return if (currentIndex > 0) {
            list[currentIndex - 1]
        } else {
            null
        }
    }

    fun clearPlaylist() {
        _playlist.value = emptyList()
        thumbnailFlows.clear()
        thumbnailCache.evictAll()
    }

    fun getThumbnailFlow(videoId: String): StateFlow<Bitmap?> {
        if (thumbnailFlows.size > 60) {
            val keysToPrune = thumbnailFlows.keys().toList().take(20)
            keysToPrune.forEach { thumbnailFlows.remove(it) }
        }
        val flow = thumbnailFlows.getOrPut(videoId) {
            MutableStateFlow(thumbnailCache.get(videoId))
        }
        if (flow.value == null) {
            preloadThumbnail(videoId)
        }
        return flow.asStateFlow()
    }

    private fun preloadThumbnail(videoId: String) {
        scope.launch {
            try {
                val cached = thumbnailCache.get(videoId)
                if (cached != null) {
                    thumbnailFlows.getOrPut(videoId) { MutableStateFlow(cached) }.value = cached
                    return@launch
                }
                val url = "https://img.youtube.com/vi/$videoId/mqdefault.jpg"
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    thumbnailCache.put(videoId, bitmap)
                    thumbnailFlows.getOrPut(videoId) { MutableStateFlow(bitmap) }.value = bitmap
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadThumbnail(videoId: String) {
        val cached = thumbnailCache.get(videoId)
        if (cached != null) {
            _thumbnailBitmap.value = cached
            return
        }
        scope.launch {
            try {
                val url = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                val connection = URL(url).openConnection().apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    thumbnailCache.put(videoId, bitmap)
                    _thumbnailBitmap.value = bitmap
                    thumbnailFlows.getOrPut(videoId) { MutableStateFlow(bitmap) }.value = bitmap
                }
            } catch (_: Exception) {
                _thumbnailBitmap.value = null
            }
        }
    }

    fun reset() {
        _currentVideoId.value = null
        _currentTitle.value = null
        _currentAuthor.value = null
        _thumbnailBitmap.value = null
        _isPlaying.value = false
        _isLoading.value = false
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        _playlist.value = _playlist.value.map { it.copy(isPlaying = false) }
    }

    fun formatTime(timeMs: Long): String {
        val totalSeconds = (timeMs / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun openInYouTube(context: Context) {
        val videoId = _currentVideoId.value ?: return
        val seconds = (_currentPositionMs.value / 1000).coerceAtLeast(0)
        val uri = Uri.parse("https://youtu.be/$videoId?t=${seconds}s")

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.youtube")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallback)
        }
    }
}

