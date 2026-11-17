package com.example.privacyshield.data

import com.example.privacyshield.filtering.DomainName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStarterDomainsTest {

    @Test
    fun allStarterDomainsAreValidAndUnique() {
        val domains = DatabaseHelper.DEFAULT_STARTER_DOMAINS

        assertTrue("Starter domains count should be > 100", domains.size > 100)

        val uniqueSet = domains.toSet()
        assertEquals("There should be no duplicate domains in DEFAULT_STARTER_DOMAINS", uniqueSet.size, domains.size)

        for (domain in domains) {
            val normalized = DomainName.normalize(domain)
            assertNotNull("Domain '$domain' failed normalization", normalized)
            assertEquals("Domain '$domain' should already be normalized", domain, normalized)
        }
    }

    @Test
    fun containsKeyAdAndTrackingNetworks() {
        val domains = DatabaseHelper.DEFAULT_STARTER_DOMAINS.toSet()

        assertTrue(domains.contains("trafficjunky.com"))
        assertTrue(domains.contains("trafficjunky.net"))
        assertTrue(domains.contains("delivery.trafficjunky.net"))
        assertTrue(domains.contains("exoclick.com"))
        assertTrue(domains.contains("juicyads.com"))
        assertTrue(domains.contains("popads.net"))
        assertTrue(domains.contains("propellerads.com"))

        assertTrue(domains.contains("doubleclick.net"))
        assertTrue(domains.contains("aax.amazon-adsystem.com"))
        assertTrue(domains.contains("adnxs.com"))
        assertTrue(domains.contains("pubmatic.com"))
        assertTrue(domains.contains("criteo.com"))
        assertTrue(domains.contains("taboola.com"))
        assertTrue(domains.contains("outbrain.com"))
        assertTrue(domains.contains("google-analytics.com"))
        assertTrue(domains.contains("pagead2.googlesyndication.com"))
    }
}
