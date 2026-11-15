package com.example.privacyshield.vpn

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ProtectionState {
    data object Disconnected : ProtectionState
    data object Connecting : ProtectionState
    data object Connected : ProtectionState
    data object Disconnecting : ProtectionState
    data class Error(val message: String) : ProtectionState
}

object ProtectionController {

    private val _state = MutableStateFlow<ProtectionState>(ProtectionState.Disconnected)
    val state: StateFlow<ProtectionState> = _state.asStateFlow()

    private var connectedTimestamp: Long = 0L

    fun getUptimeMillis(): Long {
        return if (_state.value is ProtectionState.Connected && connectedTimestamp > 0) {
            System.currentTimeMillis() - connectedTimestamp
        } else {
            0L
        }
    }

    fun updateState(newState: ProtectionState) {
        if (newState is ProtectionState.Connected && _state.value !is ProtectionState.Connected) {
            connectedTimestamp = System.currentTimeMillis()
        } else if (newState !is ProtectionState.Connected) {
            connectedTimestamp = 0L
        }
        _state.value = newState
    }

    fun start(context: Context) {
        if (_state.value is ProtectionState.Connected || _state.value is ProtectionState.Connecting) return
        updateState(ProtectionState.Connecting)
        val intent = Intent(context, PrivacyShieldVpnService::class.java).apply {
            action = PrivacyShieldVpnService.ACTION_START
        }
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            updateState(ProtectionState.Error(e.message ?: "Failed to start VPN service"))
        }
    }

    fun stop(context: Context) {
        if (_state.value is ProtectionState.Disconnected || _state.value is ProtectionState.Disconnecting) return
        updateState(ProtectionState.Disconnecting)
        val intent = Intent(context, PrivacyShieldVpnService::class.java).apply {
            action = PrivacyShieldVpnService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {
            updateState(ProtectionState.Disconnected)
        }
    }

    fun reload(context: Context) {
        if (_state.value is ProtectionState.Connected) {
            val intent = Intent(context, PrivacyShieldVpnService::class.java).apply {
                action = PrivacyShieldVpnService.ACTION_RELOAD
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }
}

