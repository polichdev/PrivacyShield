package com.example.privacyshield.network

import com.example.privacyshield.filtering.DomainRule
import com.example.privacyshield.filtering.DomainRuleMatcher
import com.example.privacyshield.filtering.FilterDecision
import com.example.privacyshield.filtering.RuleMatch
import com.example.privacyshield.filtering.RuleSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DnsPacketProcessorTest {

    private class FakeUpstreamResolver(
        private val responseToReturn: ByteArray? = ByteArray(12) { 0x77.toByte() }
    ) : UpstreamDnsResolver {
        var callCount = 0
        var lastPayload: ByteArray? = null

        override fun resolve(queryPayload: ByteArray): ByteArray? {
            callCount++
            lastPayload = queryPayload
            return responseToReturn
        }
    }

    private fun buildUdpDnsPacket(domain: String, txId: Int = 0x1234): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(txId)
        dos.writeShort(0x0100) 
        dos.writeShort(1) 
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)

        for (part in domain.split('.')) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
        dos.writeShort(1) 
        dos.writeShort(1) 
        dos.flush()

        val dnsPayload = baos.toByteArray()
        return DnsPacketSynthesizer.wrapInIpv4Udp(
            sourceIp = byteArrayOf(10, 0, 0, 2),
            destinationIp = byteArrayOf(10, 0, 0, 1),
            sourcePort = 50000,
            destinationPort = 53,
            payload = dnsPayload
        )
    }

    @Test
    fun blocksMatchingDomainWithoutCallingUpstream() {
        val rules = listOf(
            DomainRule("rule-1", "adserver.com", RuleSource.USER_BLOCK, RuleMatch.INCLUDE_SUBDOMAINS)
        )
        val matcher = DomainRuleMatcher(rules)
        val fakeResolver = FakeUpstreamResolver()
        var eventLogged = false

        val processor = DnsPacketProcessor(
            ruleMatcherProvider = { matcher },
            upstreamResolver = fakeResolver,
            onEvent = { domain, decision ->
                if (domain == "adserver.com" && decision is FilterDecision.Block) {
                    eventLogged = true
                }
            }
        )

        val packet = buildUdpDnsPacket("adserver.com")
        val result = processor.processPacket(packet, packet.size)

        assertTrue(result is PacketProcessResult.Handled)
        val handled = result as PacketProcessResult.Handled
        assertEquals(FilterDecision.Block("rule-1"), handled.decision)
        assertEquals(0, fakeResolver.callCount) 
        assertTrue(eventLogged)
    }

    @Test
    fun allowsNonMatchingDomainAndCallsUpstream() {
        val rules = listOf(
            DomainRule("rule-1", "bad.com", RuleSource.USER_BLOCK, RuleMatch.EXACT)
        )
        val matcher = DomainRuleMatcher(rules)
        val fakeResolver = FakeUpstreamResolver()
        var loggedAllow = false

        val processor = DnsPacketProcessor(
            ruleMatcherProvider = { matcher },
            upstreamResolver = fakeResolver,
            onEvent = { domain, decision ->
                if (domain == "wikipedia.org" && decision is FilterDecision.Allow) {
                    loggedAllow = true
                }
            }
        )

        val packet = buildUdpDnsPacket("wikipedia.org")
        val result = processor.processPacket(packet, packet.size)

        assertTrue(result is PacketProcessResult.Handled)
        val handled = result as PacketProcessResult.Handled
        assertEquals(FilterDecision.Allow, handled.decision)
        assertEquals(1, fakeResolver.callCount)
        assertTrue(loggedAllow)
    }

    @Test
    fun userAllowOverridesBlockRule() {
        val rules = listOf(
            DomainRule("sub-1", "analytics.google.com", RuleSource.SUBSCRIPTION, RuleMatch.EXACT),
            DomainRule("user-allow", "analytics.google.com", RuleSource.USER_ALLOW, RuleMatch.EXACT)
        )
        val matcher = DomainRuleMatcher(rules)
        val fakeResolver = FakeUpstreamResolver()

        val processor = DnsPacketProcessor(
            ruleMatcherProvider = { matcher },
            upstreamResolver = fakeResolver
        )

        val packet = buildUdpDnsPacket("analytics.google.com")
        val result = processor.processPacket(packet, packet.size)

        assertTrue(result is PacketProcessResult.Handled)
        assertEquals(FilterDecision.Allow, (result as PacketProcessResult.Handled).decision)
        assertEquals(1, fakeResolver.callCount)
    }

    @Test
    fun blocksDohBootstrapDomainsToPreventBypass() {
        val matcher = DomainRuleMatcher(emptyList())
        val fakeResolver = FakeUpstreamResolver()

        val processor = DnsPacketProcessor(
            ruleMatcherProvider = { matcher },
            upstreamResolver = fakeResolver
        )

        val packetGoogle = buildUdpDnsPacket("dns.google")
        val resultGoogle = processor.processPacket(packetGoogle, packetGoogle.size)
        assertTrue(resultGoogle is PacketProcessResult.Handled)
        assertEquals(FilterDecision.Block("doh-canary-block"), (resultGoogle as PacketProcessResult.Handled).decision)
        assertEquals(0, fakeResolver.callCount)

        val packetFirefox = buildUdpDnsPacket("use-application-dns.net")
        val resultFirefox = processor.processPacket(packetFirefox, packetFirefox.size)
        assertTrue(resultFirefox is PacketProcessResult.Handled)
        assertEquals(FilterDecision.Block("doh-canary-block"), (resultFirefox as PacketProcessResult.Handled).decision)
        assertEquals(0, fakeResolver.callCount)
    }

    @Test
    fun handlesIpv6DnsPackets() {
        val rules = listOf(
            DomainRule("rule-1", "adserver.com", RuleSource.USER_BLOCK, RuleMatch.EXACT)
        )
        val matcher = DomainRuleMatcher(rules)
        val fakeResolver = FakeUpstreamResolver()

        val processor = DnsPacketProcessor(
            ruleMatcherProvider = { matcher },
            upstreamResolver = fakeResolver
        )

        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(0x5678)
        dos.writeShort(0x0100)
        dos.writeShort(1)
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)

        for (part in "adserver.com".split('.')) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
        dos.writeShort(28)
        dos.writeShort(1)
        dos.flush()

        val dnsPayload = baos.toByteArray()
        val ipv6Packet = DnsPacketSynthesizer.wrapInIpv6Udp(
            sourceIp = ByteArray(16) { 0xfe.toByte() },
            destinationIp = ByteArray(16) { 0xfd.toByte() },
            sourcePort = 54321,
            destinationPort = 53,
            payload = dnsPayload
        )

        val result = processor.processPacket(ipv6Packet, ipv6Packet.size)
        assertTrue(result is PacketProcessResult.Handled)
        val handled = result as PacketProcessResult.Handled
        assertEquals(FilterDecision.Block("rule-1"), handled.decision)
        assertEquals(0, fakeResolver.callCount)
    }
}

