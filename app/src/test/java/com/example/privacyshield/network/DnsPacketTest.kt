package com.example.privacyshield.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

class DnsPacketTest {

    private fun createSampleDnsQuery(
        transactionId: Int = 0x1234,
        domain: String = "ads.example.com",
        qType: Int = 1
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(transactionId)
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
        dos.writeShort(qType)
        dos.writeShort(1) 
        dos.flush()
        return baos.toByteArray()
    }

    @Test
    fun parsesValidDnsQueryPayload() {
        val queryBytes = createSampleDnsQuery(0x5678, "tracker.analytics.org", 1)
        val query = DnsPacketParser.parseDnsQuery(queryBytes)

        assertNotNull(query)
        assertEquals(0x5678, query!!.transactionId)
        assertEquals(1, query.questions.size)
        assertEquals("tracker.analytics.org", query.questions[0].domain)
        assertEquals(1, query.questions[0].qType)
    }

    @Test
    fun parsesIpv4UdpDnsPacket() {
        val dnsPayload = createSampleDnsQuery(0xABCD, "doubleclick.net", 1)
        val srcIp = byteArrayOf(10, 0, 0, 2)
        val dstIp = byteArrayOf(10, 0, 0, 1)

        val fullPacket = DnsPacketSynthesizer.wrapInIpv4Udp(
            sourceIp = srcIp,
            destinationIp = dstIp,
            sourcePort = 54321,
            destinationPort = 53,
            payload = dnsPayload
        )

        val parsed = DnsPacketParser.parseUdpDns(fullPacket, fullPacket.size)
        assertNotNull(parsed)
        assertEquals(54321, parsed!!.sourcePort)
        assertEquals(53, parsed.destinationPort)
        assertEquals(0xABCD, parsed.dnsQuery.transactionId)
        assertEquals("doubleclick.net", parsed.dnsQuery.questions[0].domain)
    }

    @Test
    fun rejectsNonDnsOrMalformedPackets() {

        assertNull(DnsPacketParser.parseUdpDns(ByteArray(20), 20))

        val nonUdp = ByteArray(40)
        nonUdp[0] = 0x45
        nonUdp[9] = 6
        assertNull(DnsPacketParser.parseUdpDns(nonUdp, nonUdp.size))

        val nonPort53 = DnsPacketSynthesizer.wrapInIpv4Udp(
            sourceIp = byteArrayOf(10, 0, 0, 2),
            destinationIp = byteArrayOf(10, 0, 0, 1),
            sourcePort = 1234,
            destinationPort = 80,
            payload = ByteArray(20)
        )
        assertNull(DnsPacketParser.parseUdpDns(nonPort53, nonPort53.size))
    }

    @Test
    fun synthesizesValidBlockResponse() {
        val dnsPayload = createSampleDnsQuery(0x9999, "bad-tracker.com", 1)
        val srcIp = byteArrayOf(10, 0, 0, 2)
        val dstIp = byteArrayOf(10, 0, 0, 1)

        val fullPacket = DnsPacketSynthesizer.wrapInIpv4Udp(
            sourceIp = srcIp,
            destinationIp = dstIp,
            sourcePort = 40000,
            destinationPort = 53,
            payload = dnsPayload
        )

        val parsed = DnsPacketParser.parseUdpDns(fullPacket, fullPacket.size)!!
        val response = DnsPacketSynthesizer.createBlockResponse(parsed, ttlSeconds = 60)

        val buffer = ByteBuffer.wrap(response)
        val ihl = (buffer.get(0).toInt() and 0x0F) * 4
        val respSrcPort = buffer.getShort(ihl).toInt() and 0xFFFF
        val respDstPort = buffer.getShort(ihl + 2).toInt() and 0xFFFF

        assertEquals(53, respSrcPort)
        assertEquals(40000, respDstPort)

        val dnsRespOffset = ihl + 8
        val dnsRespBytes = response.copyOfRange(dnsRespOffset, response.size)
        val dnsBuffer = ByteBuffer.wrap(dnsRespBytes)

        val txId = dnsBuffer.short.toInt() and 0xFFFF
        val flags = dnsBuffer.short.toInt() and 0xFFFF
        val qdCount = dnsBuffer.short.toInt() and 0xFFFF
        val anCount = dnsBuffer.short.toInt() and 0xFFFF

        assertEquals(0x9999, txId)
        assertTrue("QR bit must be set (response)", (flags and 0x8000) != 0)
        assertEquals(1, qdCount)
        assertEquals(1, anCount)
    }

    @Test
    fun checksumsAreValid() {
        val payload = createSampleDnsQuery(0x1111, "test.com", 1)
        val packet = DnsPacketSynthesizer.wrapInIpv4Udp(
            sourceIp = byteArrayOf(192.toByte(), 168.toByte(), 1, 100),
            destinationIp = byteArrayOf(8, 8, 8, 8),
            sourcePort = 50000,
            destinationPort = 53,
            payload = payload
        )

        val ipChecksum = DnsPacketSynthesizer.computeInternetChecksum(packet, 0, 20)
        assertEquals(0, ipChecksum)
    }

    @Test
    fun synthesizesValidAaaaBlockResponse() {
        val dnsPayload = createSampleDnsQuery(0x2222, "ipv6-ad.net", qType = 28) 
        val parsed = DnsPacketParser.parseDnsQuery(dnsPayload)!!
        val blockPayload = DnsPacketSynthesizer.createBlockDnsPayload(parsed, ttlSeconds = 120)

        val buffer = ByteBuffer.wrap(blockPayload)
        val txId = buffer.short.toInt() and 0xFFFF
        val flags = buffer.short.toInt() and 0xFFFF
        val qdCount = buffer.short.toInt() and 0xFFFF
        val anCount = buffer.short.toInt() and 0xFFFF

        assertEquals(0x2222, txId)
        assertTrue((flags and 0x8000) != 0)
        assertEquals(1, qdCount)
        assertEquals(1, anCount)
    }
}

