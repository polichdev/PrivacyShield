package com.example.privacyshield.youtube

import java.util.regex.Pattern

data class ParsedYouTubeUrl(
    val videoId: String,
    val startSeconds: Long = 0L
)

object YouTubeUrlParser {

    private val YOUTUBE_ID_PATTERN = Pattern.compile(
        "(?:https?://)?(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|v/|embed/|shorts/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    )

    private val TIME_PATTERN = Pattern.compile("[?&](?:t|start)=([0-9hms]+)")

    fun parse(text: String?): ParsedYouTubeUrl? {
        if (text.isNullOrBlank()) return null
        val idMatcher = YOUTUBE_ID_PATTERN.matcher(text)
        if (!idMatcher.find()) return null

        val videoId = idMatcher.group(1) ?: return null
        val startSeconds = extractSeconds(text)

        return ParsedYouTubeUrl(videoId, startSeconds)
    }

    fun extractVideoId(text: String?): String? = parse(text)?.videoId

    fun extractSeconds(text: String): Long {
        val matcher = TIME_PATTERN.matcher(text)
        if (!matcher.find()) return 0L
        val raw = matcher.group(1) ?: return 0L

        if (raw.matches(Regex("^\\d+s?$"))) {
            return raw.removeSuffix("s").toLongOrNull() ?: 0L
        }

        var hours = 0L
        var minutes = 0L
        var seconds = 0L

        Regex("(\\d+)h").find(raw)?.let { hours = it.groupValues[1].toLongOrNull() ?: 0L }
        Regex("(\\d+)m").find(raw)?.let { minutes = it.groupValues[1].toLongOrNull() ?: 0L }
        Regex("(\\d+)s").find(raw)?.let { seconds = it.groupValues[1].toLongOrNull() ?: 0L }

        return hours * 3600 + minutes * 60 + seconds
    }
}

