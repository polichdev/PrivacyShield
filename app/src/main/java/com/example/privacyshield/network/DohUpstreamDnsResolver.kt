package com.example.privacyshield.network

import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

class VpnProtectingSocketFactory(
    private val delegate: SocketFactory = SocketFactory.getDefault(),
    private val socketProtector: (Socket) -> Boolean
) : SocketFactory() {

    override fun createSocket(): Socket {
        val socket = delegate.createSocket()
        socketProtector(socket)
        return socket
    }

    override fun createSocket(host: String?, port: Int): Socket {
        val socket = delegate.createSocket()
        socketProtector(socket)
        if (host != null) {
            socket.connect(InetSocketAddress(host, port))
        }
        return socket
    }

    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
        val socket = delegate.createSocket()
        if (localHost != null || localPort > 0) {
            socket.bind(InetSocketAddress(localHost, localPort))
        }
        socketProtector(socket)
        if (host != null) {
            socket.connect(InetSocketAddress(host, port))
        }
        return socket
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        val socket = delegate.createSocket()
        socketProtector(socket)
        if (host != null) {
            socket.connect(InetSocketAddress(host, port))
        }
        return socket
    }

    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
        val socket = delegate.createSocket()
        if (localAddress != null || localPort > 0) {
            socket.bind(InetSocketAddress(localAddress, localPort))
        }
        socketProtector(socket)
        if (address != null) {
            socket.connect(InetSocketAddress(address, port))
        }
        return socket
    }
}

class DohUpstreamDnsResolver(
    private val upstreamIp: String = "1.1.1.1",
    private val fallbackUdpResolver: UpstreamDnsResolver,
    private val socketProtector: (Socket) -> Boolean = { true },
    timeoutMs: Long = 1500
) : UpstreamDnsResolver {

    companion object {
        private val MEDIA_TYPE_DNS = "application/dns-message".toMediaType()

        fun resolveDohUrl(upstreamIp: String): String {
            return when (upstreamIp.trim()) {
                "1.1.1.1", "1.0.0.1" -> "https://1.1.1.1/dns-query"
                "9.9.9.9", "149.112.112.112" -> "https://dns.quad9.net/dns-query"
                "8.8.8.8", "8.8.4.4" -> "https://dns.google/dns-query"
                else -> if (upstreamIp.startsWith("http://") || upstreamIp.startsWith("https://")) {
                    upstreamIp
                } else {
                    "https://$upstreamIp/dns-query"
                }
            }
        }
    }

    private val dohUrl = resolveDohUrl(upstreamIp)

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .socketFactory(VpnProtectingSocketFactory(socketProtector = socketProtector))
            .connectionPool(ConnectionPool(8, 120, TimeUnit.SECONDS))
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .retryOnConnectionFailure(true)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return when (hostname) {
                        "1.1.1.1", "cloudflare-dns.com" -> listOf(InetAddress.getByName("1.1.1.1"))
                        "9.9.9.9", "dns.quad9.net" -> listOf(InetAddress.getByName("9.9.9.9"))
                        "8.8.8.8", "dns.google" -> listOf(InetAddress.getByName("8.8.8.8"))
                        else -> try {
                            Dns.SYSTEM.lookup(hostname)
                        } catch (_: Exception) {
                            listOf(InetAddress.getByName(upstreamIp))
                        }
                    }
                }
            })
            .build()
    }

    override fun resolve(queryPayload: ByteArray): ByteArray? {
        if (queryPayload.size < 2) return null
        val txId0 = queryPayload[0]
        val txId1 = queryPayload[1]

        try {
            val requestBody = queryPayload.toRequestBody(MEDIA_TYPE_DNS)
            val request = Request.Builder()
                .url(dohUrl)
                .post(requestBody)
                .header("Accept", "application/dns-message")
                .header("Content-Type", "application/dns-message")
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.size >= 12) {
                        bytes[0] = txId0
                        bytes[1] = txId1
                        return bytes
                    }
                }
            }
        } catch (_: Exception) {
            // If DoH fails or times out, fallback to UDP
        }

        return fallbackUdpResolver.resolve(queryPayload)
    }

    override fun close() {
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        } catch (_: Exception) {}
        try {
            fallbackUdpResolver.close()
        } catch (_: Exception) {}
    }
}
