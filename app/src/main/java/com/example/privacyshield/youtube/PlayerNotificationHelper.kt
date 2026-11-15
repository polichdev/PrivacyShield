package com.example.privacyshield.youtube

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.privacyshield.MainActivity
import com.example.privacyshield.R

class PlayerNotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "yt_audio_channel"
        const val NOTIFICATION_ID = 3003
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_player_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_player_channel_desc)
                setShowBadge(false)
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        currentTitle: String,
        currentAuthor: String,
        isPlaying: Boolean,
        isPreparing: Boolean,
        currentPos: Long,
        mediaSession: MediaSession?
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_player", true)
        }
        val pendingLaunch = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replayIntent = Intent(context, YouTubeAudioService::class.java).apply {
            action = YouTubeAudioService.ACTION_REPLAY_10
        }
        val pendingReplay = PendingIntent.getService(
            context, 10, replayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = Intent(context, YouTubeAudioService::class.java).apply {
            action = YouTubeAudioService.ACTION_TOGGLE_PAUSE
        }
        val pendingToggle = PendingIntent.getService(
            context, 1, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = Intent(context, YouTubeAudioService::class.java).apply {
            action = YouTubeAudioService.ACTION_FORWARD_10
        }
        val pendingForward = PendingIntent.getService(
            context, 11, forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openYtIntent = Intent(context, YouTubeAudioService::class.java).apply {
            action = YouTubeAudioService.ACTION_OPEN_IN_YOUTUBE
        }
        val pendingOpenYt = PendingIntent.getService(
            context, 2, openYtIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, YouTubeAudioService::class.java).apply {
            action = YouTubeAudioService.ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            context, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = when {
            isPreparing -> context.getString(R.string.notif_player_preparing)
            isPlaying -> "$currentAuthor • ${YouTubePlayerState.formatTime(currentPos)}"
            else -> "${context.getString(R.string.notif_player_paused)} • ${YouTubePlayerState.formatTime(currentPos)}"
        }

        val actionIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow
        val actionText = if (isPlaying) context.getString(R.string.player_pause) else context.getString(R.string.notif_player_play)
        val rewindText = context.getString(R.string.notif_player_rewind_10)
        val forwardText = context.getString(R.string.notif_player_forward_10)
        val stopText = context.getString(R.string.notif_player_stop)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(currentTitle)
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_stat_player)
            .setContentIntent(pendingLaunch)
            .setDeleteIntent(pendingStop)
            .addAction(R.drawable.ic_replay_10, rewindText, pendingReplay)
            .addAction(actionIcon, actionText, pendingToggle)
            .addAction(R.drawable.ic_forward_10, forwardText, pendingForward)
            .addAction(R.drawable.ic_close, stopText, pendingStop)
            .addAction(R.drawable.ic_youtube, "YouTube", pendingOpenYt)
            .setOngoing(isPlaying || isPreparing)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        mediaSession?.let { session ->
            builder.setStyle(MediaStyleNotificationHelper.MediaStyle(session).setShowActionsInCompactView(0, 1, 2))
        }

        YouTubePlayerState.thumbnailBitmap.value?.let { bitmap ->
            builder.setLargeIcon(bitmap)
        }

        return builder.build()
    }
}
