package com.example.privacyshield.network

import android.util.LruCache
import com.example.privacyshield.filtering.DomainRuleMatcher
import com.example.privacyshield.filtering.FilterDecision
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

sealed interface PacketProcessResult {
    data object Ignore : PacketProcessResult
    data object Drop : PacketProcessResult
    data class Handled(
        val responsePacket: ByteArray,
        val decision: FilterDecision,
        val domain: String
    ) : PacketProcessResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Handled) return false
            return decision == other.decision && domain == other.domain && responsePacket.contentEquals(other.responsePacket)
        }

        override fun hashCode(): Int {
            return 31 * (31 * decision.hashCode() + domain.hashCode()) + responsePacket.contentHashCode()
        }
    }
}

interface UpstreamDnsResolver {
    fun resolve(queryPayload: ByteArray): ByteArray?
}

class UdpUpstreamDnsResolver(
    private val upstreamIp: String = "1.1.1.1",
    private val fallbackIp: String = if (upstreamIp == "1.1.1.1") "8.8.8.8" else "1.1.1.1",
    private val port: Int = 53,
    private val timeoutMs: Int = 1200,
    private val socketProtector: (DatagramSocket) -> Boolean = { true }
) : UpstreamDnsResolver {

    private val primaryAddress: InetAddress? by lazy {
        try { InetAddress.getByName(upstreamIp) } catch (_: Exception) { null }
    }
    private val fallbackAddress: InetAddress? by lazy {
        try { InetAddress.getByName(fallbackIp) } catch (_: Exception) { null }
    }

    private val allSockets = java.util.concurrent.ConcurrentHashMap.newKeySet<DatagramSocket>()

    private val threadSocket = object : ThreadLocal<DatagramSocket?>() {
        override fun initialValue(): DatagramSocket? = null
    }

    private fun getOrCreateSocket(): DatagramSocket? {
        var socket = threadSocket.get()
        if (socket != null && !socket.isClosed) {
            return socket
        }
        return try {
            socket = DatagramSocket()
            try {
                socket.receiveBufferSize = 65536
                socket.sendBufferSize = 65536
            } catch (_: Exception) {}
            if (!socketProtector(socket)) {
                socket.close()
                return null
            }
            socket.soTimeout = timeoutMs
            threadSocket.set(socket)
            allSockets.add(socket)
            socket
        } catch (_: Exception) {
            null
        }
    }

    override fun resolve(queryPayload: ByteArray): ByteArray? {
        val socket = getOrCreateSocket() ?: return null
        if (queryPayload.size < 2) return null
        val queryTxId = ((queryPayload[0].toInt() and 0xFF) shl 8) or (queryPayload[1].toInt() and 0xFF)

        val primary = primaryAddress
        val fallback = fallbackAddress

        return try {

            if (primary != null) {
                val sendPacketPrimary = DatagramPacket(queryPayload, queryPayload.size, primary, port)
                socket.send(sendPacketPrimary)
            }
            if (fallback != null && fallback != primary) {
                val sendPacketFallback = DatagramPacket(queryPayload, queryPayload.size, fallback, port)
                socket.send(sendPacketFallback)
            }

            val receiveBuffer = ByteArray(4096)
            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)

            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val remaining = (deadline - System.currentTimeMillis()).toInt()
                if (remaining <= 0) break
                socket.soTimeout = remaining
                receivePacket.length = receiveBuffer.size
                socket.receive(receivePacket)
                val sender = receivePacket.address
                if (sender != primary && sender != fallback) {
                    continue
                }
                if (receivePacket.length >= 2) {
                    val offset = receivePacket.offset
                    val respTxId = ((receiveBuffer[offset].toInt() and 0xFF) shl 8) or (receiveBuffer[offset + 1].toInt() and 0xFF)
                    if (respTxId == queryTxId) {
                        return receivePacket.data.copyOfRange(offset, offset + receivePacket.length)
                    }

                }
            }
            null
        } catch (_: SocketTimeoutException) {

            try {
                allSockets.remove(socket)
                socket.close()
            } catch (_: Exception) {}
            threadSocket.set(null)
            null
        } catch (_: Exception) {

            try {
                allSockets.remove(socket)
                socket.close()
            } catch (_: Exception) {}
            threadSocket.set(null)
            null
        }
    }

    fun close() {
        for (s in allSockets) {
            try { s.close() } catch (_: Exception) {}
        }
        allSockets.clear()
        threadSocket.set(null)
    }
}

class DnsMemoryCache(private val maxEntries: Int = 2048) {
    private data class CachedEntry(val payload: ByteArray, val expiresAt: Long)
    private val cache = object : java.util.LinkedHashMap<String, CachedEntry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedEntry>?): Boolean {
            return size > maxEntries
        }
    }

    fun get(domain: String, qType: Int, txId: Int): ByteArray? {
        val key = "$domain#$qType"
        val entry = synchronized(cache) { cache[key] } ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            synchronized(cache) { cache.remove(key) }
            return null
        }
        val copy = entry.payload.clone()
        copy[0] = (txId ushr 8).toByte()
        copy[1] = (txId and 0xFF).toByte()
        return copy
    }

    fun put(domain: String, qType: Int, payload: ByteArray, ttlSeconds: Int = 180) {
        val key = "$domain#$qType"
        val expiresAt = System.currentTimeMillis() + (ttlSeconds.coerceIn(30, 600) * 1000L)
        synchronized(cache) {
            cache[key] = CachedEntry(payload, expiresAt)
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }
}

class DnsPacketProcessor(
    private val ruleMatcherProvider: () -> DomainRuleMatcher,
    private val upstreamResolver: UpstreamDnsResolver,
    private val onEvent: (domain: String, decision: FilterDecision) -> Unit = { _, _ -> }
) {

    private val dnsCache = DnsMemoryCache()

    companion object {

        private val ESSENTIAL_SYSTEM_DOMAINS = setOf(
            "connectivitycheck.gstatic.com",
            "connectivitycheck.android.com",
            "clients3.google.com",
            "play.googleapis.com",
            "dns.google",
            "android.clients.google.com",
            "play-lh.googleusercontent.com",
            "play.google.com"
        )
    }

    fun processPacket(packet: ByteArray, length: Int): PacketProcessResult {
        val parsed = DnsPacketParser.parseUdpDns(packet, length)
        if (parsed == null) {

            val rst = DnsPacketSynthesizer.createTcpRstResponse(packet, length)
            return if (rst != null) {
                PacketProcessResult.Handled(rst, FilterDecision.Allow, "tcp-reset")
            } else {
                PacketProcessResult.Ignore
            }
        }

        val query = parsed.dnsQuery
        val primaryQuestion = query.questions.firstOrNull() ?: return PacketProcessResult.Drop

        val domain = primaryQuestion.domain
        val qType = primaryQuestion.qType
        val matcher = ruleMatcherProvider()
        val decision = if (domain in ESSENTIAL_SYSTEM_DOMAINS || domain.endsWith(".gvt1.com")) {
            FilterDecision.Allow
        } else {
            matcher.evaluate(domain)
        }

        onEvent(domain, decision)

        return when (decision) {
            is FilterDecision.Block -> {
                val blockResponse = DnsPacketSynthesizer.createBlockResponse(parsed)
                PacketProcessResult.Handled(blockResponse, decision, domain)
            }
            is FilterDecision.Allow -> {

                val cached = dnsCache.get(domain, qType, query.transactionId)
                if (cached != null) {
                    val fullResponse = DnsPacketSynthesizer.wrapUpstreamResponse(parsed, cached)
                    PacketProcessResult.Handled(fullResponse, decision, domain)
                } else {
                    val upstreamResponse = upstreamResolver.resolve(query.rawPayload)
                    if (upstreamResponse != null) {
                        dnsCache.put(domain, qType, upstreamResponse, 180)
                        val fullResponse = DnsPacketSynthesizer.wrapUpstreamResponse(parsed, upstreamResponse)
                        PacketProcessResult.Handled(fullResponse, decision, domain)
                    } else {
                        PacketProcessResult.Drop
                    }
                }
            }
            is FilterDecision.InvalidDomain -> {
                PacketProcessResult.Drop
            }
        }
    }
}

