package com.example.privacyshield.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.privacyshield.MainActivity
import com.example.privacyshield.R

class VpnNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "privacy_shield_protection_channel_v2"
        const val NOTIFICATION_ID = 1001
    }

    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, VpnActionReceiver::class.java).apply {
            action = VpnActionReceiver.ACTION_STOP_VPN
        }
        val pendingStop = PendingIntent.getBroadcast(
            context, 101, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.state_connected)
        val content = context.getString(R.string.notif_vpn_content)
        val bigTitle = context.getString(R.string.notif_vpn_big_title)
        val bigText = context.getString(R.string.notif_vpn_big_text)
        val stopAction = context.getString(R.string.action_stop)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_nav_shield)
            .setContentIntent(pendingLaunch)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(bigTitle)
                    .bigText(bigText)
            )
            .addAction(
                R.drawable.ic_close,
                stopAction,
                pendingStop
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        return builder.build()
    }
}
