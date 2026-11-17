package com.example.privacyshield.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DohUpstreamDnsResolverTest {

    @Test
    fun resolvesDohUrlsCorrectly() {
        assertEquals("https://1.1.1.1/dns-query", DohUpstreamDnsResolver.resolveDohUrl("1.1.1.1"))
        assertEquals("https://1.1.1.1/dns-query", DohUpstreamDnsResolver.resolveDohUrl("1.0.0.1"))
        assertEquals("https://dns.quad9.net/dns-query", DohUpstreamDnsResolver.resolveDohUrl("9.9.9.9"))
        assertEquals("https://dns.google/dns-query", DohUpstreamDnsResolver.resolveDohUrl("8.8.8.8"))
        assertEquals("https://custom.dns/query", DohUpstreamDnsResolver.resolveDohUrl("https://custom.dns/query"))
        assertEquals("https://192.168.1.1/dns-query", DohUpstreamDnsResolver.resolveDohUrl("192.168.1.1"))
    }

    @Test
    fun fallsBackToUdpOnNetworkFailure() {
        val expectedResponse = byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0x00, 0x01)
        val fakeUdpResolver = object : UpstreamDnsResolver {
            var invoked = false
            override fun resolve(queryPayload: ByteArray): ByteArray? {
                invoked = true
                return expectedResponse
            }
        }

        val dohResolver = DohUpstreamDnsResolver(
            upstreamIp = "127.0.0.1:65534",
            fallbackUdpResolver = fakeUdpResolver,
            timeoutMs = 100
        )

        val query = byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01)
        val result = dohResolver.resolve(query)

        assertNotNull(result)
        assertEquals(expectedResponse, result)
        assertEquals(true, fakeUdpResolver.invoked)
    }
}
