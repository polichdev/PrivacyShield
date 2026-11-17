package com.example.privacyshield.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.example.privacyshield.MainActivity
import com.example.privacyshield.R
import com.example.privacyshield.data.AppPolicyRepository
import com.example.privacyshield.data.DatabaseHelper
import com.example.privacyshield.data.RuleRepository
import com.example.privacyshield.data.SettingsRepository
import com.example.privacyshield.data.StatisticsRepository
import com.example.privacyshield.filtering.FilterDecision
import com.example.privacyshield.network.DnsPacketProcessor
import com.example.privacyshield.network.DohUpstreamDnsResolver
import com.example.privacyshield.network.PacketProcessResult
import com.example.privacyshield.network.UdpUpstreamDnsResolver
import com.example.privacyshield.network.UpstreamDnsResolver
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PrivacyShieldVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.example.privacyshield.action.START"
        const val ACTION_STOP = "com.example.privacyshield.action.STOP"
        const val ACTION_RELOAD = "com.example.privacyshield.action.RELOAD"

        const val CHANNEL_ID = "privacy_shield_protection_channel_v2"
        const val NOTIFICATION_ID = 1001

        const val VPN_ADDRESS = "10.0.0.2"
        const val DNS_SERVER_ADDRESS = "10.0.0.1"
        const val VPN_ROUTE = "10.0.0.1"
        const val VPN_MTU = 1500
    }

    private var tunnelInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null
    private var workerThreadPool: ExecutorService? = null
    private var statsExecutor: ExecutorService? = null
    private var upstreamResolver: UpstreamDnsResolver? = null

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var ruleRepository: RuleRepository
    private lateinit var appPolicyRepository: AppPolicyRepository
    private lateinit var statisticsRepository: StatisticsRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var vpnNotificationManager: VpnNotificationManager

    override fun onCreate() {
        super.onCreate()
        vpnNotificationManager = VpnNotificationManager(this)
        vpnNotificationManager.createNotificationChannel()
        dbHelper = DatabaseHelper.getInstance(this)
        ruleRepository = RuleRepository(dbHelper)
        appPolicyRepository = AppPolicyRepository(dbHelper)
        statisticsRepository = StatisticsRepository(dbHelper)
        settingsRepository = SettingsRepository(dbHelper)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        when (action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
            }
            ACTION_START -> {
                startVpn()
            }
            ACTION_RELOAD -> {
                if (isRunning.get()) {
                    reloadVpn()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun reloadVpn() {
        stopVpn()
        startVpn()
    }

    private fun startVpn() {
        if (isRunning.get()) return

        try {
            startForegroundNotification()

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(VPN_MTU)
                .addAddress(VPN_ADDRESS, 32)
                .addDnsServer(DNS_SERVER_ADDRESS)
                .addRoute(VPN_ROUTE, 32)

            val filteredPackages = appPolicyRepository.getFilteredPackages()
            if (filteredPackages.isNotEmpty()) {
                for (pkg in filteredPackages) {
                    try {
                        builder.addAllowedApplication(pkg)
                    } catch (_: Exception) {}
                }
                try {
                    builder.addAllowedApplication(packageName)
                } catch (_: Exception) {}
            }

            val pfd = builder.establish()
            if (pfd == null) {
                ProtectionController.updateState(ProtectionState.Error(getString(R.string.error_vpn_establish_failed)))
                stopSelf()
                return
            }

            tunnelInterface = pfd
            isRunning.set(true)
            ProtectionController.updateState(ProtectionState.Connected)

            val pool = Executors.newFixedThreadPool(16)
            workerThreadPool = pool
            val statsPool = Executors.newSingleThreadExecutor()
            statsExecutor = statsPool

            val upstreamDns = settingsRepository.getUpstreamDns()
            val udpFallback = UdpUpstreamDnsResolver(
                upstreamIp = upstreamDns,
                socketProtector = { socket -> protect(socket) }
            )
            val resolver = DohUpstreamDnsResolver(
                upstreamIp = upstreamDns,
                fallbackUdpResolver = udpFallback,
                socketProtector = { socket -> protect(socket) }
            )
            upstreamResolver = resolver

            val packetProcessor = DnsPacketProcessor(
                ruleMatcherProvider = { ruleRepository.getCompiledMatcher() },
                upstreamResolver = resolver,
                onEvent = { domain, decision ->
                    val isBlocked = decision is FilterDecision.Block
                    val ruleId = if (isBlocked) (decision as FilterDecision.Block).ruleId else null
                    statisticsRepository.recordEvent(domain, isBlocked, ruleId)
                }
            )

            workerThread = Thread({
                runTunnelLoop(pfd, packetProcessor, pool)
            }, "PrivacyShield-TunnelWorker").apply { start() }

            registerNetworkCallback()

        } catch (e: Exception) {
            ProtectionController.updateState(ProtectionState.Error(e.message ?: getString(R.string.error_vpn_start_failed)))
            stopVpn()
            stopSelf()
        }
    }

    private fun runTunnelLoop(
        pfd: ParcelFileDescriptor,
        processor: DnsPacketProcessor,
        executor: ExecutorService
    ) {
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val outputLock = Any()
        val buffer = ByteArray(32768)

        try {
            while (isRunning.get()) {
                val length = inputStream.read(buffer)
                if (length <= 0) continue

                val packetCopy = buffer.copyOf(length)

                executor.execute {
                    if (!isRunning.get()) return@execute
                    try {
                        val result = processor.processPacket(packetCopy, length)
                        if (result is PacketProcessResult.Handled && isRunning.get()) {
                            synchronized(outputLock) {
                                outputStream.write(result.responsePacket)
                                outputStream.flush()
                            }
                        }
                    } catch (_: Exception) {

                    }
                }
            }
        } catch (_: IOException) {

        } catch (_: Exception) {

        } finally {
            if (isRunning.get()) {
                stopVpn()
            }
        }
    }

    private fun stopVpn() {
        isRunning.set(false)
        unregisterNetworkCallback()
        try {
            workerThread?.interrupt()
            workerThread = null
        } catch (_: Exception) {}

        try {
            workerThreadPool?.shutdownNow()
            workerThreadPool = null
        } catch (_: Exception) {}

        try {
            statsExecutor?.shutdown()
            statsExecutor = null
        } catch (_: Exception) {}

        try {
            upstreamResolver?.close()
            upstreamResolver = null
        } catch (_: Exception) {}

        try {
            statisticsRepository.flushPending()
        } catch (_: Exception) {}

        try {
            tunnelInterface?.close()
            tunnelInterface = null
        } catch (_: Exception) {}

        ProtectionController.updateState(ProtectionState.Disconnected)
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = connectivityManager ?: return

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        setUnderlyingNetworks(arrayOf(network))
                    } catch (_: Exception) {}
                }
            }

            override fun onLost(network: Network) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    try {
                        setUnderlyingNetworks(null)
                    } catch (_: Exception) {}
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, networkCallback!!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val activeNet = cm.activeNetwork
                if (activeNet != null) {
                    setUnderlyingNetworks(arrayOf(activeNet))
                }
            }
        } catch (_: Exception) {}
    }

    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
            networkCallback = null
        }
    }

    override fun onRevoke() {
        stopVpn()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        val notification = vpnNotificationManager.buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(VpnNotificationManager.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(VpnNotificationManager.NOTIFICATION_ID, notification)
        }
    }
}

