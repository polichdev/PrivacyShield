package com.example.privacyshield.network

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.nio.ByteBuffer

data class DnsQuestion(
    val domain: String,
    val qType: Int,
    val qClass: Int
)

data class DnsQuery(
    val transactionId: Int,
    val flags: Int,
    val questions: List<DnsQuestion>,
    val rawPayload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DnsQuery) return false
        return transactionId == other.transactionId && rawPayload.contentEquals(other.rawPayload)
    }

    override fun hashCode(): Int {
        return 31 * transactionId + rawPayload.contentHashCode()
    }
}

data class ParsedDnsDatagram(
    val sourceIp: ByteArray,
    val destinationIp: ByteArray,
    val sourcePort: Int,
    val destinationPort: Int,
    val dnsQuery: DnsQuery
)

object DnsPacketParser {

    fun parseUdpDns(packet: ByteArray, length: Int): ParsedDnsDatagram? {
        if (length < 28) return null 

        val versionAndIhl = packet[0].toInt() and 0xFF
        val version = versionAndIhl ushr 4
        if (version != 4) return null

        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 8) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null 

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

        if (dstPort != 53) return null 

        val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        if (udpLength < 8 || ihl + udpLength > length) return null

        val dnsOffset = ihl + 8
        val dnsLength = udpLength - 8
        if (dnsLength < 12) return null 

        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLength)
        val dnsQuery = parseDnsQuery(dnsPayload) ?: return null

        return ParsedDnsDatagram(
            sourceIp = srcIp,
            destinationIp = dstIp,
            sourcePort = srcPort,
            destinationPort = dstPort,
            dnsQuery = dnsQuery
        )
    }

    fun parseDnsQuery(payload: ByteArray): DnsQuery? {
        if (payload.size < 12) return null
        val buffer = ByteBuffer.wrap(payload)
        val transactionId = buffer.short.toInt() and 0xFFFF
        val flags = buffer.short.toInt() and 0xFFFF
        val qdCount = buffer.short.toInt() and 0xFFFF
        val anCount = buffer.short.toInt() and 0xFFFF
        val nsCount = buffer.short.toInt() and 0xFFFF
        val arCount = buffer.short.toInt() and 0xFFFF

        val isQuery = (flags and 0x8000) == 0
        if (!isQuery || qdCount < 1) return null

        val questions = mutableListOf<DnsQuestion>()
        for (i in 0 until qdCount) {
            val domain = readQName(buffer) ?: return null
            if (buffer.remaining() < 4) return null
            val qType = buffer.short.toInt() and 0xFFFF
            val qClass = buffer.short.toInt() and 0xFFFF
            questions.add(DnsQuestion(domain, qType, qClass))
        }

        return DnsQuery(
            transactionId = transactionId,
            flags = flags,
            questions = questions,
            rawPayload = payload
        )
    }

    private fun readQName(buffer: ByteBuffer): String? {
        val labels = mutableListOf<String>()
        val startPosition = buffer.position()
        var jumped = false
        var iterations = 0

        while (buffer.hasRemaining()) {
            if (++iterations > 128) return null 
            val length = buffer.get().toInt() and 0xFF
            if (length == 0) break

            if ((length and 0xC0) == 0xC0) {

                if (!buffer.hasRemaining()) return null
                val pointerOffset = ((length and 0x3F) shl 8) or (buffer.get().toInt() and 0xFF)
                if (pointerOffset >= buffer.capacity()) return null
                if (!jumped) {
                    jumped = true
                }
                val currentPos = buffer.position()
                buffer.position(pointerOffset)
                val pointedName = readQName(buffer)
                buffer.position(currentPos)
                return if (pointedName != null) {
                    if (labels.isEmpty()) pointedName else "${labels.joinToString(".")}.$pointedName"
                } else null
            } else {
                if (length > 63 || buffer.remaining() < length) return null
                val labelBytes = ByteArray(length)
                buffer.get(labelBytes)
                labels.add(String(labelBytes, Charsets.US_ASCII))
            }
        }
        return labels.joinToString(".")
    }
}

object DnsPacketSynthesizer {

    fun createBlockResponse(
        parsed: ParsedDnsDatagram,
        ttlSeconds: Int = 300
    ): ByteArray {
        val dnsResponsePayload = createBlockDnsPayload(parsed.dnsQuery, ttlSeconds)
        return wrapInIpv4Udp(
            sourceIp = parsed.destinationIp, 
            destinationIp = parsed.sourceIp,
            sourcePort = parsed.destinationPort,
            destinationPort = parsed.sourcePort,
            payload = dnsResponsePayload
        )
    }

    fun wrapUpstreamResponse(
        parsed: ParsedDnsDatagram,
        upstreamDnsPayload: ByteArray
    ): ByteArray {
        return wrapInIpv4Udp(
            sourceIp = parsed.destinationIp,
            destinationIp = parsed.sourceIp,
            sourcePort = parsed.destinationPort,
            destinationPort = parsed.sourcePort,
            payload = upstreamDnsPayload
        )
    }

    fun createBlockDnsPayload(query: DnsQuery, ttlSeconds: Int = 300): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)

        dos.writeShort(query.transactionId)

        val flags = 0x8180 
        dos.writeShort(flags)

        dos.writeShort(query.questions.size)

        val answerableQueries = query.questions.filter { it.qType == 1 || it.qType == 28 }
        dos.writeShort(answerableQueries.size)

        dos.writeShort(0)
        dos.writeShort(0)

        for (q in query.questions) {
            writeDomainLabels(dos, q.domain)
            dos.writeShort(q.qType)
            dos.writeShort(q.qClass)
        }

        for (q in answerableQueries) {
            writeDomainLabels(dos, q.domain)
            dos.writeShort(q.qType)
            dos.writeShort(1) 
            dos.writeInt(ttlSeconds)
            if (q.qType == 1) {
                dos.writeShort(4) 
                dos.write(byteArrayOf(0, 0, 0, 0)) 
            } else {
                dos.writeShort(16) 
                dos.write(ByteArray(16)) 
            }
        }

        dos.flush()
        return baos.toByteArray()
    }

    private fun writeDomainLabels(dos: DataOutputStream, domain: String) {
        val parts = domain.split('.')
        for (part in parts) {
            val bytes = part.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0) 
    }

    fun wrapInIpv4Udp(
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        sourcePort: Int,
        destinationPort: Int,
        payload: ByteArray
    ): ByteArray {
        val ipHeaderLength = 20
        val udpHeaderLength = 8
        val totalLength = ipHeaderLength + udpHeaderLength + payload.size

        val packet = ByteBuffer.allocate(totalLength)

        packet.put(0x45.toByte()) 
        packet.put(0x00.toByte()) 
        packet.putShort(totalLength.toShort())
        packet.putShort(0x1234.toShort()) 
        packet.putShort(0x4000.toShort()) 
        packet.put(64.toByte()) 
        packet.put(17.toByte()) 
        packet.putShort(0.toShort()) 
        packet.put(sourceIp)
        packet.put(destinationIp)

        val ipChecksum = computeInternetChecksum(packet.array(), 0, ipHeaderLength)
        packet.putShort(10, ipChecksum.toShort())

        val udpOffset = ipHeaderLength
        val udpLength = udpHeaderLength + payload.size
        packet.putShort(sourcePort.toShort())
        packet.putShort(destinationPort.toShort())
        packet.putShort(udpLength.toShort())
        packet.putShort(0.toShort()) 
        packet.put(payload)

        return packet.array()
    }

    fun createTcpRstResponse(packet: ByteArray, length: Int): ByteArray? {
        if (length < 40) return null

        val versionAndIhl = packet[0].toInt() and 0xFF
        if (versionAndIhl ushr 4 != 4) return null
        val ihl = (versionAndIhl and 0x0F) * 4
        if (ihl < 20 || length < ihl + 20) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 6) return null 

        val srcIp = packet.copyOfRange(12, 16)
        val dstIp = packet.copyOfRange(16, 20)

        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

        if (dstPort != 53 && dstPort != 853) return null

        val seq = ((packet[ihl + 4].toLong() and 0xFF) shl 24) or
                ((packet[ihl + 5].toLong() and 0xFF) shl 16) or
                ((packet[ihl + 6].toLong() and 0xFF) shl 8) or
                (packet[ihl + 7].toLong() and 0xFF)

        val ack = ((packet[ihl + 8].toLong() and 0xFF) shl 24) or
                ((packet[ihl + 9].toLong() and 0xFF) shl 16) or
                ((packet[ihl + 10].toLong() and 0xFF) shl 8) or
                (packet[ihl + 11].toLong() and 0xFF)

        val tcpFlags = packet[ihl + 13].toInt() and 0xFF
        if ((tcpFlags and 0x04) != 0) return null 

        val isSyn = (tcpFlags and 0x02) != 0
        val isAck = (tcpFlags and 0x10) != 0

        val totalLength = 40 
        val response = ByteBuffer.allocate(totalLength)

        response.put(0x45.toByte())
        response.put(0x00.toByte())
        response.putShort(totalLength.toShort())
        response.putShort(0.toShort()) 
        response.putShort(0x4000.toShort()) 
        response.put(64.toByte()) 
        response.put(6.toByte()) 
        response.putShort(0.toShort()) 
        response.put(dstIp) 
        response.put(srcIp)

        val ipChecksum = computeInternetChecksum(response.array(), 0, 20)
        response.putShort(10, ipChecksum.toShort())

        response.putShort(dstPort.toShort()) 
        response.putShort(srcPort.toShort())

        if (isAck) {
            response.putInt(ack.toInt())
            response.putInt(0)
            response.put((0x50).toByte()) 
            response.put((0x04).toByte()) 
        } else {
            response.putInt(0)
            val newAck = (seq + if (isSyn) 1 else 0).toInt()
            response.putInt(newAck)
            response.put((0x50).toByte()) 
            response.put((0x14).toByte()) 
        }

        response.putShort(0.toShort()) 
        response.putShort(0.toShort()) 
        response.putShort(0.toShort()) 

        val tcpChecksum = computeTcpChecksum(dstIp, srcIp, response.array(), 20, 20)
        response.putShort(36, tcpChecksum.toShort())

        return response.array()
    }

    fun computeTcpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        tcpOffset: Int,
        tcpLength: Int
    ): Int {
        var sum = 0

        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)

        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)

        sum += 6 
        sum += tcpLength

        var i = tcpOffset
        val end = tcpOffset + tcpLength
        while (i < end - 1) {
            val high = packet[i].toInt() and 0xFF
            val low = packet[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i < end) {
            val high = packet[i].toInt() and 0xFF
            sum += (high shl 8)
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        val result = sum.inv() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }

    fun computeInternetChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length

        while (i < end - 1) {
            val high = data[i].toInt() and 0xFF
            val low = data[i + 1].toInt() and 0xFF
            val word = (high shl 8) or low
            sum += word
            i += 2
        }

        if (i < end) {
            val high = data[i].toInt() and 0xFF
            sum += (high shl 8)
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return sum.inv() and 0xFFFF
    }

    fun computeUdpChecksum(
        srcIp: ByteArray,
        dstIp: ByteArray,
        packet: ByteArray,
        udpOffset: Int,
        udpLength: Int
    ): Int {
        var sum = 0

        sum += ((srcIp[0].toInt() and 0xFF) shl 8) or (srcIp[1].toInt() and 0xFF)
        sum += ((srcIp[2].toInt() and 0xFF) shl 8) or (srcIp[3].toInt() and 0xFF)

        sum += ((dstIp[0].toInt() and 0xFF) shl 8) or (dstIp[1].toInt() and 0xFF)
        sum += ((dstIp[2].toInt() and 0xFF) shl 8) or (dstIp[3].toInt() and 0xFF)

        sum += 17

        sum += udpLength

        var i = udpOffset
        val end = udpOffset + udpLength
        while (i < end - 1) {
            val high = packet[i].toInt() and 0xFF
            val low = packet[i + 1].toInt() and 0xFF
            sum += (high shl 8) or low
            i += 2
        }
        if (i < end) {
            val high = packet[i].toInt() and 0xFF
            sum += (high shl 8)
        }

        while (sum shr 16 != 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        val result = sum.inv() and 0xFFFF
        return if (result == 0) 0xFFFF else result
    }
}

