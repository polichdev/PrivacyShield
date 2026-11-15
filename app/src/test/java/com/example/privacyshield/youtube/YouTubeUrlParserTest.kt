package com.example.privacyshield.youtube

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun parseStandardUrl() {
        val parsed = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertNotNull(parsed)
        assertEquals("dQw4w9WgXcQ", parsed?.videoId)
        assertEquals(0L, parsed?.startSeconds)
    }

    @Test
    fun parseShortUrlWithTimestamp() {
        val parsed = YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ?t=1h30m15s")
        assertNotNull(parsed)
        assertEquals("dQw4w9WgXcQ", parsed?.videoId)
        assertEquals(5415L, parsed?.startSeconds)
    }

    @Test
    fun parseShortsUrl() {
        val parsed = YouTubeUrlParser.parse("https://youtube.com/shorts/dQw4w9WgXcQ")
        assertNotNull(parsed)
        assertEquals("dQw4w9WgXcQ", parsed?.videoId)
    }

    @Test
    fun parseSharedTextWithExtraText() {
        val sharedText = "Check out this song: https://youtu.be/dQw4w9WgXcQ?t=45s it's great!"
        val parsed = YouTubeUrlParser.parse(sharedText)
        assertNotNull(parsed)
        assertEquals("dQw4w9WgXcQ", parsed?.videoId)
        assertEquals(45L, parsed?.startSeconds)
    }

    @Test
    fun parseInvalidUrl() {
        assertNull(YouTubeUrlParser.parse("https://example.com/not-youtube"))
        assertNull(YouTubeUrlParser.parse(""))
        assertNull(YouTubeUrlParser.parse(null))
    }
}

