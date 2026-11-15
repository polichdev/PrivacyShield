package com.example.privacyshield.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.privacyshield.MainActivity
import com.example.privacyshield.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

object YouTubeAudioCache {
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun get(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, "audio_stream_cache")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val evictor = LeastRecentlyUsedCacheEvictor(150 * 1024 * 1024L) 
            val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }
}

class YouTubeAudioService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var loadJob: Job? = null

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private var currentVideoId: String? = null
    private var currentTitle: String = "YouTube Audio"
    private var currentAuthor: String = "YouTube"
    private var initialStartSeconds: Long = 0L

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var notificationHelper: PlayerNotificationHelper

    override fun onCreate() {
        super.onCreate()
        notificationHelper = PlayerNotificationHelper(this)
        notificationHelper.createNotificationChannel()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        OfflineDownloadManager.init(this)
        initExoPlayer()
        registerNetworkCallback()

        startOrUpdateForeground(isPreparing = true)

        serviceScope.launch {
            YouTubePlayerState.thumbnailBitmap.collect {
                if (currentVideoId != null) {
                    startOrUpdateForeground(isPreparing = false)
                }
            }
        }
    }

    private fun registerNetworkCallback() {
        val cm = connectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {

                serviceScope.launch(Dispatchers.Main) {
                    val vid = currentVideoId
                    val player = exoPlayer
                    if (vid != null && player != null) {
                        if (player.playerError != null || (!player.isPlaying && YouTubePlayerState.isLoading.value)) {
                            val resumeSec = (player.currentPosition / 1000L).coerceAtLeast(0L)
                            loadAndPlay(vid, resumeSec)
                        }
                    }
                }
            }
        }

        try {
            cm.registerNetworkCallback(request, networkCallback!!)
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

    private fun initExoPlayer() {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Android 14; Mobile)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(15000)

        val cache = YouTubeAudioCache.get(applicationContext)
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            this,
            cacheDataSourceFactory
        )

        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                 15_000,
                 60_000,
                 1_500,
                 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(ProgressiveMediaSource.Factory(defaultDataSourceFactory))
            .setLoadControl(loadControl)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player.setAudioAttributes(audioAttributes, true)
        player.setWakeMode(C.WAKE_MODE_NETWORK)

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        YouTubePlayerState.setLoading(false)
                        val duration = player.duration.coerceAtLeast(0L)
                        val pos = player.currentPosition.coerceAtLeast(0L)
                        YouTubePlayerState.updateProgress(pos, duration)
                        YouTubePlayerState.updateMetadata(
                            videoId = currentVideoId,
                            title = currentTitle,
                            author = currentAuthor,
                            playing = player.isPlaying
                        )
                        startOrUpdateForeground(isPreparing = false)
                        if (player.isPlaying) {
                            startProgressLoop()
                        }
                    }
                    Player.STATE_BUFFERING -> {
                        startOrUpdateForeground(isPreparing = true)
                    }
                    Player.STATE_ENDED -> {
                        stopProgressLoop()
                        YouTubePlayerState.setLoading(false)
                        YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, false)
                        startOrUpdateForeground(isPreparing = false)

                        val nextItem = YouTubePlayerState.getNextQueueItem(currentVideoId)
                        if (nextItem != null) {
                            loadAndPlay(nextItem.videoId, 0L)
                        }
                    }
                    Player.STATE_IDLE -> {
                        YouTubePlayerState.setLoading(false)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                YouTubePlayerState.setLoading(false)
                YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, isPlaying)
                startOrUpdateForeground(isPreparing = false)
                if (isPlaying) {
                    startProgressLoop()
                } else {
                    stopProgressLoop()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val vid = currentVideoId
                if (vid != null && OfflineDownloadManager.isDownloaded(this@YouTubeAudioService, vid)) {
                    val offlineFile = OfflineDownloadManager.getOfflineFile(this@YouTubeAudioService, vid)
                    val offlineUri = android.net.Uri.fromFile(offlineFile)
                    val currentPos = exoPlayer?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    val mediaItem = MediaItem.fromUri(offlineUri)
                    exoPlayer?.setMediaItem(mediaItem)
                    exoPlayer?.prepare()
                    if (currentPos > 0) {
                        exoPlayer?.seekTo(currentPos)
                    }
                    exoPlayer?.play()
                    YouTubePlayerState.setLoading(false)
                    YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, true)
                    startOrUpdateForeground(isPreparing = false)
                    startProgressLoop()
                    return
                }
                stopProgressLoop()
                YouTubePlayerState.setLoading(false)
                YouTubePlayerState.updateMetadata(currentVideoId, getString(R.string.player_error_connection), currentAuthor, false)
                startOrUpdateForeground(isPreparing = false)
            }
        })

        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
                val startSec = intent.getLongExtra(EXTRA_START_SECONDS, 0L)
                if (!videoId.isNullOrBlank()) {
                    loadAndPlay(videoId, startSec)
                }
            }
            ACTION_ENQUEUE -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
                val startSec = intent.getLongExtra(EXTRA_START_SECONDS, 0L)
                if (!videoId.isNullOrBlank()) {
                    handleEnqueue(videoId, startSec)
                }
            }
            ACTION_TOGGLE_PAUSE -> {
                if (currentVideoId == null || exoPlayer == null) {
                    val first = YouTubePlayerState.playlist.value.firstOrNull()
                    if (first != null) {
                        val seekSec = (first.lastPositionMs / 1000L).coerceAtLeast(0L)
                        loadAndPlay(first.videoId, seekSec)
                    }
                } else {
                    togglePlayback()
                }
            }
            ACTION_SEEK -> {
                val posMs = intent.getLongExtra(EXTRA_SEEK_MS, 0L)
                seekPlayback(posMs)
            }
            ACTION_REPLAY_10 -> {
                val current = exoPlayer?.currentPosition ?: 0L
                seekPlayback((current - 10000L).coerceAtLeast(0L))
            }
            ACTION_FORWARD_10 -> {
                val current = exoPlayer?.currentPosition ?: 0L
                val duration = exoPlayer?.duration ?: 0L
                seekPlayback((current + 10000L).coerceAtMost(duration))
            }
            ACTION_OPEN_IN_YOUTUBE -> {
                pausePlayback()
                YouTubePlayerState.openInYouTube(this)
            }
            ACTION_STOP -> {
                stopPlayback()
                return START_NOT_STICKY
            }
            ACTION_SWITCH_TO_OFFLINE -> {
                val videoId = intent.getStringExtra(EXTRA_VIDEO_ID)
                if (!videoId.isNullOrBlank() && videoId == currentVideoId) {
                    switchToOfflineFile(videoId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopPlayback()
    }

    private fun isPlaceholderTitle(title: String): Boolean {
        return title.startsWith("Завантаження") ||
            title.startsWith("Loading") ||
            title.startsWith(getString(R.string.player_status_loading)) ||
            title.startsWith(getString(R.string.player_status_loading_stream))
    }

    private fun handleEnqueue(videoId: String, startSeconds: Long) {
        val isCurrentlyActive = currentVideoId != null && (exoPlayer?.isPlaying == true || YouTubePlayerState.isLoading.value)
        val loadingText = getString(R.string.player_status_loading)

        val savedMeta = OfflineDownloadManager.getSavedMetadata(this, videoId)
        val initialTitle = savedMeta?.title ?: loadingText
        val initialAuthor = savedMeta?.author ?: "YouTube"

        YouTubePlayerState.addToPlaylist(
            QueueItem(
                videoId = videoId,
                title = initialTitle,
                author = initialAuthor,
                isPlaying = !isCurrentlyActive
            )
        )

        serviceScope.launch {
            val result = YouTubeStreamResolver.resolveAudioStream(videoId)
            result.onSuccess { stream ->
                if (!isPlaceholderTitle(stream.title)) {
                    YouTubePlayerState.updateItemMetadata(videoId, stream.title, stream.author)
                    OfflineDownloadManager.saveMetadata(this@YouTubeAudioService, videoId, stream.title, stream.author)
                }
            }
        }

        if (!isCurrentlyActive) {
            loadAndPlay(videoId, startSeconds)
        }
    }

    private fun loadAndPlay(videoId: String, startSeconds: Long) {
        if (exoPlayer == null) {
            initExoPlayer()
        }

        loadJob?.cancel()
        stopProgressLoop()

        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        } catch (_: Exception) {}

        currentVideoId = videoId
        initialStartSeconds = startSeconds

        val loadingStreamText = getString(R.string.player_status_loading_stream)

        val savedMeta = OfflineDownloadManager.getSavedMetadata(this, videoId)
        val existingItem = YouTubePlayerState.playlist.value.firstOrNull { it.videoId == videoId }
        val hasRealTitle = existingItem != null && !isPlaceholderTitle(existingItem.title)

        currentTitle = when {
            hasRealTitle -> existingItem!!.title
            savedMeta != null -> savedMeta.title
            else -> loadingStreamText
        }
        currentAuthor = when {
            hasRealTitle -> existingItem!!.author
            savedMeta != null -> savedMeta.author
            else -> "YouTube"
        }

        YouTubePlayerState.setLoading(true)
        startOrUpdateForeground(isPreparing = true)

        YouTubePlayerState.addToPlaylist(
            QueueItem(
                videoId = videoId,
                title = currentTitle,
                author = currentAuthor,
                isPlaying = true
            )
        )

        loadJob = serviceScope.launch {
            if (OfflineDownloadManager.isDownloaded(this@YouTubeAudioService, videoId)) {
                val offlineFile = OfflineDownloadManager.getOfflineFile(this@YouTubeAudioService, videoId)
                val meta = OfflineDownloadManager.getSavedMetadata(this@YouTubeAudioService, videoId)
                if (meta != null && isPlaceholderTitle(currentTitle)) {
                    currentTitle = meta.title
                    currentAuthor = meta.author
                    YouTubePlayerState.updateItemMetadata(videoId, meta.title, meta.author)
                }
                playStreamUrl(android.net.Uri.fromFile(offlineFile).toString(), initialStartSeconds)
                return@launch
            }

            val result = kotlinx.coroutines.withTimeoutOrNull(7000) {
                YouTubeStreamResolver.resolveAudioStream(videoId)
            }

            if (result != null && result.isSuccess) {
                val stream = result.getOrThrow()
                currentTitle = stream.title
                currentAuthor = stream.author
                OfflineDownloadManager.saveMetadata(this@YouTubeAudioService, videoId, stream.title, stream.author)
                YouTubePlayerState.updateItemMetadata(videoId, stream.title, stream.author)
                playStreamUrl(stream.audioUrl, initialStartSeconds)
            } else {
                val fallbackUrl = "https://yt.chocolatemoo53.com/latest_version?id=$videoId&itag=140"
                playStreamUrl(fallbackUrl, initialStartSeconds)
            }
        }
    }

    private fun playStreamUrl(streamUrl: String, startSeconds: Long) {
        if (exoPlayer == null) {
            initExoPlayer()
        }
        val player = exoPlayer ?: return
        try {
            val mediaItem = MediaItem.fromUri(streamUrl)
            player.setMediaItem(mediaItem)
            player.prepare()
            if (startSeconds > 0) {
                player.seekTo(startSeconds * 1000)
            }
            player.play()
            YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, true)
            startOrUpdateForeground(isPreparing = false)
            startProgressLoop()

            serviceScope.launch {
                delay(5000)
                if (YouTubePlayerState.isLoading.value) {
                    YouTubePlayerState.setLoading(false)
                }
            }
        } catch (e: Exception) {
            YouTubePlayerState.setLoading(false)
            currentTitle = getString(R.string.player_error_start_failed)
            YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, false)
            startOrUpdateForeground(isPreparing = false)
        }
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = serviceScope.launch {
            while (isActive) {
                exoPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            val pos = player.currentPosition.coerceAtLeast(0L)
                            val dur = player.duration.coerceAtLeast(0L)
                            YouTubePlayerState.updateProgress(pos, dur)
                        }
                    } catch (_: Exception) {}
                }
                delay(500)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun seekPlayback(positionMs: Long) {
        exoPlayer?.let { player ->
            try {
                player.seekTo(positionMs)
                YouTubePlayerState.updateProgress(positionMs, player.duration.coerceAtLeast(0L))
            } catch (_: Exception) {}
        }
    }

    private fun togglePlayback() {
        if (loadJob?.isActive == true) {
            loadJob?.cancel()
            loadJob = null
            YouTubePlayerState.setLoading(false)
            YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, false)
            startOrUpdateForeground(isPreparing = false)
            return
        }
        if (exoPlayer == null) {
            initExoPlayer()
        }
        val player = exoPlayer ?: return
        if (player.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun pausePlayback() {
        exoPlayer?.pause()
        stopProgressLoop()
        YouTubePlayerState.setLoading(false)
        YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, false)
        startOrUpdateForeground(isPreparing = false)
    }

    private fun switchToOfflineFile(videoId: String) {
        val player = exoPlayer ?: return
        if (OfflineDownloadManager.isDownloaded(this, videoId)) {
            val offlineFile = OfflineDownloadManager.getOfflineFile(this, videoId)
            val offlineUri = android.net.Uri.fromFile(offlineFile)
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            if (currentUri != offlineUri) {
                val currentPos = player.currentPosition.coerceAtLeast(0L)
                val wasPlaying = player.isPlaying
                val mediaItem = MediaItem.fromUri(offlineUri)
                player.setMediaItem(mediaItem)
                player.prepare()
                if (currentPos > 0) {
                    player.seekTo(currentPos)
                }
                if (wasPlaying) {
                    player.play()
                }
            }
        }
    }

    private fun resumePlayback() {
        if (exoPlayer == null) {
            initExoPlayer()
        }
        val player = exoPlayer
        val vid = currentVideoId
        if (player == null || vid == null) {
            val first = YouTubePlayerState.playlist.value.firstOrNull()
            if (first != null) {
                val seekSec = (first.lastPositionMs / 1000L).coerceAtLeast(0L)
                loadAndPlay(first.videoId, seekSec)
            }
            return
        }

        if (OfflineDownloadManager.isDownloaded(this, vid)) {
            val offlineFile = OfflineDownloadManager.getOfflineFile(this, vid)
            val offlineUri = android.net.Uri.fromFile(offlineFile)
            val currentUri = player.currentMediaItem?.localConfiguration?.uri
            if (currentUri != offlineUri || player.playerError != null) {
                val currentPos = player.currentPosition.coerceAtLeast(0L)
                val mediaItem = MediaItem.fromUri(offlineUri)
                player.setMediaItem(mediaItem)
                player.prepare()
                if (currentPos > 0) {
                    player.seekTo(currentPos)
                }
                player.play()
                YouTubePlayerState.setLoading(false)
                YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, true)
                startOrUpdateForeground(isPreparing = false)
                startProgressLoop()
                return
            }
        }

        if (player.playerError != null || player.playbackState == Player.STATE_IDLE) {
            player.prepare()
        }
        player.play()
        YouTubePlayerState.setLoading(false)
        YouTubePlayerState.updateMetadata(currentVideoId, currentTitle, currentAuthor, true)
        startOrUpdateForeground(isPreparing = false)
        startProgressLoop()
    }

    private fun stopPlayback() {
        loadJob?.cancel()
        loadJob = null
        stopProgressLoop()

        try {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}

        mediaSession?.release()
        mediaSession = null

        YouTubePlayerState.reset()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun startOrUpdateForeground(isPreparing: Boolean) {
        val notification = notificationHelper.buildNotification(
            currentTitle = currentTitle,
            currentAuthor = currentAuthor,
            isPlaying = exoPlayer?.isPlaying == true,
            isPreparing = isPreparing,
            currentPos = exoPlayer?.currentPosition ?: 0L,
            mediaSession = mediaSession
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterNetworkCallback()
        stopProgressLoop()
        loadJob?.cancel()
        serviceScope.cancel()

        try {
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}

        mediaSession?.release()
        mediaSession = null

        YouTubePlayerState.reset()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "yt_audio_channel"
        const val NOTIFICATION_ID = 3003

        const val ACTION_PLAY = "com.example.privacyshield.action.YT_AUDIO_PLAY"
        const val ACTION_ENQUEUE = "com.example.privacyshield.action.YT_AUDIO_ENQUEUE"
        const val ACTION_TOGGLE_PAUSE = "com.example.privacyshield.action.YT_AUDIO_TOGGLE"
        const val ACTION_SEEK = "com.example.privacyshield.action.YT_AUDIO_SEEK"
        const val ACTION_REPLAY_10 = "com.example.privacyshield.action.YT_REPLAY_10"
        const val ACTION_FORWARD_10 = "com.example.privacyshield.action.YT_FORWARD_10"
        const val ACTION_OPEN_IN_YOUTUBE = "com.example.privacyshield.action.YT_OPEN_APP"
        const val ACTION_STOP = "com.example.privacyshield.action.YT_AUDIO_STOP"
        const val ACTION_SWITCH_TO_OFFLINE = "com.example.privacyshield.action.YT_AUDIO_SWITCH_TO_OFFLINE"

        const val EXTRA_VIDEO_ID = "extra_video_id"
        const val EXTRA_START_SECONDS = "extra_start_seconds"
        const val EXTRA_SEEK_MS = "extra_seek_ms"

        fun notifyOfflineFileReady(context: Context, videoId: String) {
            val intent = Intent(context, YouTubeAudioService::class.java).apply {
                action = ACTION_SWITCH_TO_OFFLINE
                putExtra(EXTRA_VIDEO_ID, videoId)
            }
            context.startService(intent)
        }

        fun start(context: Context, videoId: String, startSeconds: Long = 0L) {
            val intent = Intent(context, YouTubeAudioService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_START_SECONDS, startSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun enqueue(context: Context, videoId: String, startSeconds: Long = 0L) {
            val intent = Intent(context, YouTubeAudioService::class.java).apply {
                action = ACTION_ENQUEUE
                putExtra(EXTRA_VIDEO_ID, videoId)
                putExtra(EXTRA_START_SECONDS, startSeconds)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun toggle(context: Context) {
            val intent = Intent(context, YouTubeAudioService::class.java).apply {
                action = ACTION_TOGGLE_PAUSE
            }
            context.startService(intent)
        }

        fun replay10(context: Context) {
            val intent = Intent(context, YouTubeAudioService::class.java).apply {
                action = ACTION_REPLAY_10
            }
            context.startService(intent)
        }

        fun forward10(context: Context) {
            val intent = Intent(context, YouTubeAudioService::class.java).apply {
                action = ACTION_FORWARD_10
            }
            context.startService(intent)
        }

        fun seek(context: Context, positionMs: Long) {
            val intent = Intent(context, YouTubeAudioService::class.java).apply {
                action = ACTION_SEEK
                putExtra(EXTRA_SEEK_MS, positionMs)
            }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, YouTubeAudioService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}

