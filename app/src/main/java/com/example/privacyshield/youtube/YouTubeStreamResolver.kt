package com.example.privacyshield.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class YouTubeAudioStream(
    val videoId: String,
    val title: String,
    val author: String,
    val audioUrl: String,
    val thumbnailUrl: String,
    val cachedAt: Long = System.currentTimeMillis()
)

object YouTubeStreamResolver {

    private const val PRIMARY_INSTANCE = "https://yt.chocolatemoo53.com"

    private val streamCache = object : java.util.LinkedHashMap<String, YouTubeAudioStream>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YouTubeAudioStream>?): Boolean {
            return size > 30
        }
    }
    private const val CACHE_EXPIRATION_MS = 2 * 60 * 60 * 1000L 

    suspend fun resolveAudioStream(videoId: String): Result<YouTubeAudioStream> = withContext(Dispatchers.IO) {

        synchronized(streamCache) {
            val cached = streamCache[videoId]
            if (cached != null && (System.currentTimeMillis() - cached.cachedAt) < CACHE_EXPIRATION_MS) {
                return@withContext Result.success(cached)
            }
        }

        val oembedDeferred = async { fetchOEmbed(videoId) }
        val streamUrlDeferred = async {
            fetchProxiedStreamUrl(PRIMARY_INSTANCE, videoId)
                ?: "$PRIMARY_INSTANCE/latest_version?id=$videoId&itag=140"
        }

        val oembed = oembedDeferred.await()
        val title = oembed?.first ?: "YouTube Audio"
        val author = oembed?.second ?: "YouTube"
        val thumbnail = oembed?.third ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val streamUrl = streamUrlDeferred.await()

        val stream = YouTubeAudioStream(
            videoId = videoId,
            title = title,
            author = author,
            audioUrl = streamUrl,
            thumbnailUrl = thumbnail
        )

        synchronized(streamCache) {
            streamCache[videoId] = stream
        }

        Result.success(stream)
    }

    private fun fetchOEmbed(videoId: String): Triple<String, String, String>? {
        return try {
            val url = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 4000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val title = json.optString("title", "YouTube Audio")
                val author = json.optString("author_name", "YouTube")
                val thumb = json.optString("thumbnail_url", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                Triple(title, author, thumb)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchProxiedStreamUrl(baseUrl: String, videoId: String): String? {
        try {
            val apiUrl = "$baseUrl/api/v1/videos/$videoId"
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 5000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                if (responseText.isNotBlank() && responseText.trimStart().startsWith("{")) {
                    val json = JSONObject(responseText)
                    val adaptiveFormats = json.optJSONArray("adaptiveFormats")
                    if (adaptiveFormats != null && adaptiveFormats.length() > 0) {
                        var bestRawUrl: String? = null
                        var bestScore = Long.MIN_VALUE

                        for (i in 0 until adaptiveFormats.length()) {
                            val format = adaptiveFormats.getJSONObject(i)
                            val type = format.optString("type", "")
                            val container = format.optString("container", "")
                            val bitrate = format.optInt("bitrate", 0)

                            if (type.startsWith("audio/") || container == "m4a" || container == "webm") {
                                val rawUrl = format.optString("url", "")
                                if (rawUrl.isNotBlank()) {
                                    val isOriginal = rawUrl.contains("acont%3Doriginal") || rawUrl.contains("acont=original")
                                    val isDubbed = rawUrl.contains("acont%3Ddubbed") || rawUrl.contains("acont=dubbed")
                                    val isUkrainian = rawUrl.contains("lang%3Duk") || rawUrl.contains("lang=uk") || rawUrl.contains("lang%3Duk-UA")
                                    val isEnglishDub = isDubbed && (rawUrl.contains("lang%3Den") || rawUrl.contains("lang=en"))

                                    var score = 0L
                                    if (isUkrainian) score += 5_000_000L
                                    if (isOriginal && !isDubbed) score += 3_000_000L
                                    if (isDubbed) score -= 2_000_000L
                                    if (isEnglishDub) score -= 4_000_000L

                                    if (container == "m4a") score += 50_000L
                                    else if (container == "webm") score += 25_000L
                                    score += (bitrate / 1000).toLong()

                                    if (score > bestScore || bestRawUrl == null) {
                                        bestScore = score
                                        bestRawUrl = rawUrl
                                    }
                                }
                            }
                        }

                        if (!bestRawUrl.isNullOrBlank()) {

                            return if (bestRawUrl.contains("googlevideo.com")) {
                                try {
                                    val uri = URI(bestRawUrl)
                                    "$baseUrl${uri.rawPath}?${uri.rawQuery}"
                                } catch (_: Exception) {
                                    bestRawUrl
                                }
                            } else {
                                bestRawUrl
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return "$baseUrl/latest_version?id=$videoId&itag=140"
    }

    fun clearCache() {
        synchronized(streamCache) {
            streamCache.clear()
        }
    }
}

