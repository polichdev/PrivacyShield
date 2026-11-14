package com.example.privacyshield.filtering

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class HostsParserTest {

    @Test
    fun parsesStandardHostsEntries() {
        val content = """
            # This is a comment
            127.0.0.1 localhost
            0.0.0.0 ads.example.com
            127.0.0.1 tracker.com # inline comment
            0.0.0.0 multi1.com multi2.com
        """.trimIndent()

        val result = HostsParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))

        assertFalse(result.contains("localhost"))
        assertTrue(result.contains("ads.example.com"))
        assertTrue(result.contains("tracker.com"))
        assertTrue(result.contains("multi1.com"))
        assertTrue(result.contains("multi2.com"))
        assertEquals(4, result.size)
    }

    @Test
    fun parsesPlainDomainListAndAdGuardSyntax() {
        val content = """
            ! Title: Sample List
            ! Description: Sample blocklist
            banner.example.net
            ||analytics.provider.org^
            bad..domain..com
            # Another comment
            sub.domain.co.uk
        """.trimIndent()

        val result = HostsParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))

        assertTrue(result.contains("banner.example.net"))
        assertTrue(result.contains("analytics.provider.org"))
        assertTrue(result.contains("sub.domain.co.uk"))
        assertFalse(result.contains("bad..domain..com")) 
        assertEquals(3, result.size)
    }

    @Test
    fun handlesEmptyAndMalformedLines() {
        val content = "\n\n   \n# empty\n! nothing\n;;; comment\n"
        val result = HostsParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun deduplicatesDomains() {
        val content = """
            0.0.0.0 dup.com
            127.0.0.1 dup.com
            dup.com
            ||dup.com^
        """.trimIndent()

        val result = HostsParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)))
        assertEquals(listOf("dup.com"), result)
    }
}

