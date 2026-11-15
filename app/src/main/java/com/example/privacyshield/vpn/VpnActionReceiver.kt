package com.example.privacyshield.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class VpnActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_VPN = "com.example.privacyshield.action.STOP_VPN"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_VPN) {
            ProtectionController.stop(context)
        }
    }
}

