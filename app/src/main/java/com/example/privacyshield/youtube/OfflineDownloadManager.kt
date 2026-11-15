package com.example.privacyshield.youtube

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class OfflineMetadata(
    val title: String,
    val author: String
)

object OfflineDownloadManager {

    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    fun init(context: Context) {
        val dir = getOfflineDir(context)
        val files = dir.listFiles { _, name -> name.endsWith(".m4a") } ?: emptyArray()
        val ids = files.filter { it.length() > 5000 }.map { it.name.removeSuffix(".m4a") }.toSet()
        _downloadedIds.value = ids
        for (id in ids) {
            val meta = getSavedMetadata(context, id)
            if (meta != null && YouTubePlayerState.playlist.value.none { it.videoId == id }) {
                YouTubePlayerState.addToPlaylist(
                    QueueItem(
                        videoId = id,
                        title = meta.title,
                        author = meta.author
                    )
                )
            }
        }
    }

    fun isDownloaded(context: Context, videoId: String): Boolean {
        val file = getOfflineFile(context, videoId)
        return file.exists() && file.length() > 5000
    }

    fun getOfflineFile(context: Context, videoId: String): File {
        return File(getOfflineDir(context), "$videoId.m4a")
    }

    private fun getMetadataFile(context: Context, videoId: String): File {
        return File(getOfflineDir(context), "$videoId.meta")
    }

    fun saveMetadata(context: Context, videoId: String, title: String, author: String) {
        try {
            if (title.isNotBlank() && !title.startsWith("Завантаження") && !title.startsWith("Loading")) {
                val file = getMetadataFile(context, videoId)
                file.writeText("$title\n$author")
            }
        } catch (_: Exception) {}
    }

    fun getSavedMetadata(context: Context, videoId: String): OfflineMetadata? {
        return try {
            val file = getMetadataFile(context, videoId)
            if (file.exists()) {
                val lines = file.readLines()
                if (lines.isNotEmpty()) {
                    val title = lines.getOrNull(0)?.trim() ?: ""
                    val author = lines.getOrNull(1)?.trim() ?: "YouTube"
                    if (title.isNotBlank() && !title.startsWith("Завантаження") && !title.startsWith("Loading")) {
                        OfflineMetadata(title, author)
                    } else null
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getOfflineDir(context: Context): File {
        val dir = File(context.filesDir, "offline_audio")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    suspend fun download(context: Context, videoId: String, directUrl: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (isDownloaded(context, videoId)) return@withContext true

        _downloadingIds.value = _downloadingIds.value + videoId
        val targetFile = getOfflineFile(context, videoId)
        val tempFile = File(context.filesDir, "temp_$videoId.tmp")

        var resolvedTitle: String? = null
        var resolvedAuthor: String? = null

        val currentItem = YouTubePlayerState.playlist.value.firstOrNull { it.videoId == videoId }
        if (currentItem != null && !currentItem.title.startsWith("Завантаження") && !currentItem.title.startsWith("Loading")) {
            resolvedTitle = currentItem.title
            resolvedAuthor = currentItem.author
        }

        val urlsToTry = mutableListOf<String>()
        if (!directUrl.isNullOrBlank()) {
            urlsToTry.add(directUrl)
        }
        val streamResult = YouTubeStreamResolver.resolveAudioStream(videoId).getOrNull()
        if (streamResult != null) {
            if (resolvedTitle == null && streamResult.title.isNotBlank() && !streamResult.title.startsWith("Завантаження") && !streamResult.title.startsWith("Loading")) {
                resolvedTitle = streamResult.title
                resolvedAuthor = streamResult.author
            }
            if (streamResult.audioUrl.isNotBlank() && !urlsToTry.contains(streamResult.audioUrl)) {
                urlsToTry.add(streamResult.audioUrl)
            }
        }
        val fallback = "https://yt.chocolatemoo53.com/latest_version?id=$videoId&itag=140"
        if (!urlsToTry.contains(fallback)) {
            urlsToTry.add(fallback)
        }

        try {
            for (attemptUrl in urlsToTry) {
                var currentUrl = attemptUrl
                var conn: HttpURLConnection? = null
                var cookieHeader: String? = null
                try {
                    for (redirect in 0..7) {
                        conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 12000
                            readTimeout = 30000
                            instanceFollowRedirects = false
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                            setRequestProperty("Referer", "https://yt.chocolatemoo53.com/")
                            setRequestProperty("Accept", "*/*")
                            if (!cookieHeader.isNullOrBlank()) {
                                setRequestProperty("Cookie", cookieHeader)
                            }
                        }
                        val code = conn.responseCode
                        val newCookie = conn.getHeaderField("Set-Cookie")
                        if (!newCookie.isNullOrBlank()) {
                            cookieHeader = newCookie.split(';').firstOrNull()
                        }

                        if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                            code == HttpURLConnection.HTTP_MOVED_TEMP ||
                            code == 307 ||
                            code == 308 ||
                            code == 303) {
                            val location = conn.getHeaderField("Location") ?: break
                            currentUrl = if (location.startsWith("http")) location else URL(URL(currentUrl), location).toString()
                            conn.disconnect()
                            continue
                        }
                        break
                    }

                    val finalConn = conn ?: continue
                    val code = finalConn.responseCode
                    if (code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) {
                        finalConn.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (tempFile.exists() && tempFile.length() > 5000) {
                            if (targetFile.exists()) targetFile.delete()
                            val renamed = tempFile.renameTo(targetFile)
                            if (renamed || targetFile.exists()) {
                                _downloadedIds.value = _downloadedIds.value + videoId
                                if (!resolvedTitle.isNullOrBlank()) {
                                    saveMetadata(context, videoId, resolvedTitle, resolvedAuthor ?: "YouTube")
                                    YouTubePlayerState.updateItemMetadata(videoId, resolvedTitle, resolvedAuthor ?: "YouTube")
                                }
                                YouTubeAudioService.notifyOfflineFileReady(context, videoId)
                                return@withContext true
                            }
                        }
                    }
                    finalConn.disconnect()
                } catch (_: Exception) {
                    try { conn?.disconnect() } catch (_: Exception) {}
                    try { if (tempFile.exists()) tempFile.delete() } catch (_: Exception) {}
                }
            }
        } finally {
            try { if (tempFile.exists()) tempFile.delete() } catch (_: Exception) {}
            _downloadingIds.value = _downloadingIds.value - videoId
        }
        false
    }

    fun delete(context: Context, videoId: String): Boolean {
        val file = getOfflineFile(context, videoId)
        val metaFile = getMetadataFile(context, videoId)
        try { metaFile.delete() } catch (_: Exception) {}
        val deleted = file.delete()
        if (deleted || !file.exists()) {
            _downloadedIds.value = _downloadedIds.value - videoId
            return true
        }
        return false
    }
}
