package com.example.privacyshield.ui.viewmodels

import android.app.Activity
import android.content.Context
import android.net.VpnService
import androidx.activity.result.ActivityResultLauncher
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privacyshield.data.ProtectionStats
import com.example.privacyshield.data.RuleRepository
import com.example.privacyshield.data.StatisticsRepository
import com.example.privacyshield.vpn.ProtectionController
import com.example.privacyshield.vpn.ProtectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(
    private val statisticsRepository: StatisticsRepository,
    private val ruleRepository: RuleRepository
) : ViewModel() {

    val protectionState: StateFlow<ProtectionState> = ProtectionController.state

    val statsState: StateFlow<ProtectionStats> = statisticsRepository.statsState

    private val _activeRulesCount = MutableStateFlow(0)
    val activeRulesCount: StateFlow<Int> = _activeRulesCount.asStateFlow()

    private val _uptimeMillis = MutableStateFlow(0L)
    val uptimeMillis: StateFlow<Long> = _uptimeMillis.asStateFlow()

    init {
        viewModelScope.launch {
            ruleRepository.rulesVersion.collect {
                withContext(Dispatchers.IO) {
                    val custom = ruleRepository.getCustomRules()
                    val blocklists = ruleRepository.getBlocklists()
                    val subRules = blocklists.filter { it.enabled }.sumOf { it.ruleCount }
                    _activeRulesCount.value = custom.size + subRules
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                if (protectionState.value is ProtectionState.Connected) {
                    _uptimeMillis.value = ProtectionController.getUptimeMillis()
                } else {
                    _uptimeMillis.value = 0L
                }
                delay(1000)
            }
        }
    }

    fun onToggleProtection(context: Context, vpnPrepareLauncher: ActivityResultLauncher<Intent>) {
        when (val state = protectionState.value) {
            is ProtectionState.Connected, is ProtectionState.Connecting -> {
                ProtectionController.stop(context)
            }
            is ProtectionState.Disconnected, is ProtectionState.Error -> {
                val prepareIntent = VpnService.prepare(context)
                if (prepareIntent != null) {
                    vpnPrepareLauncher.launch(prepareIntent)
                } else {
                    ProtectionController.start(context)
                }
            }
            is ProtectionState.Disconnecting -> {}
        }
    }
}
