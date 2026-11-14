package com.example.privacyshield.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleRepositoryUrlTest {

    @Test
    fun testValidBlocklistUrls() {
        assertTrue(RuleRepository.isValidBlocklistUrl("https://adaway.org/hosts.txt"))
        assertTrue(RuleRepository.isValidBlocklistUrl("http://example.com/blocklist.txt"))
        assertTrue(RuleRepository.isValidBlocklistUrl("https://sub.domain.org/path/to/rules?format=hosts"))
        assertTrue(RuleRepository.isValidBlocklistUrl("  https://adaway.org/hosts.txt  "))
    }

    @Test
    fun testInvalidBlocklistUrls() {
        assertFalse(RuleRepository.isValidBlocklistUrl(""))
        assertFalse(RuleRepository.isValidBlocklistUrl("   "))
        assertFalse(RuleRepository.isValidBlocklistUrl("ftp://example.com/hosts.txt"))
        assertFalse(RuleRepository.isValidBlocklistUrl("file:///android_asset/hosts.txt"))
        assertFalse(RuleRepository.isValidBlocklistUrl("javascript:alert(1)"))
        assertFalse(RuleRepository.isValidBlocklistUrl("https://"))
        assertFalse(RuleRepository.isValidBlocklistUrl("https:// bad domain .com/list"))
        assertFalse(RuleRepository.isValidBlocklistUrl("not a valid url"))
    }
}
