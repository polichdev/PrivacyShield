package com.example.privacyshield.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.privacyshield.data.Blocklist
import com.example.privacyshield.data.CustomRule
import com.example.privacyshield.data.RuleRepository
import com.example.privacyshield.filtering.DomainName
import com.example.privacyshield.filtering.RuleMatch
import com.example.privacyshield.filtering.RuleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FiltersViewModel(
    private val ruleRepository: RuleRepository
) : ViewModel() {

    private val _blocklists = MutableStateFlow<List<Blocklist>>(emptyList())
    val blocklists: StateFlow<List<Blocklist>> = _blocklists.asStateFlow()

    private val _customRules = MutableStateFlow<List<CustomRule>>(emptyList())
    val customRules: StateFlow<List<CustomRule>> = _customRules.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        viewModelScope.launch {
            ruleRepository.rulesVersion.collect {
                loadRules()
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun loadRules() {
        viewModelScope.launch(Dispatchers.IO) {
            _blocklists.value = ruleRepository.getBlocklists()
            _customRules.value = ruleRepository.getCustomRules()
        }
    }

    fun addBlocklist(name: String, url: String, onComplete: (Boolean) -> Unit) {
        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        if (trimmedName.isEmpty() || !RuleRepository.isValidBlocklistUrl(trimmedUrl)) {
            onComplete(false)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val id = ruleRepository.addBlocklist(trimmedName, trimmedUrl)
            if (id != null) {
                _isRefreshing.value = true
                ruleRepository.downloadAndRefreshBlocklist(id)
                _isRefreshing.value = false
                loadRules()
                withContext(Dispatchers.Main) { onComplete(true) }
            } else {
                withContext(Dispatchers.Main) { onComplete(false) }
            }
        }
    }

    fun toggleBlocklist(id: String, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleRepository.toggleBlocklist(id, enabled)
            loadRules()
        }
    }

    fun deleteBlocklist(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleRepository.deleteBlocklist(id)
            loadRules()
        }
    }

    fun refreshBlocklist(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            ruleRepository.downloadAndRefreshBlocklist(id)
            _isRefreshing.value = false
            loadRules()
        }
    }

    fun refreshAllBlocklists(onComplete: (successCount: Int, failCount: Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            val currentLists = ruleRepository.getBlocklists()
            var successes = 0
            var failures = 0
            for (list in currentLists) {
                val ok = ruleRepository.downloadAndRefreshBlocklist(list.id)
                if (ok) successes++ else failures++
            }
            _isRefreshing.value = false
            loadRules()
            withContext(Dispatchers.Main) {
                onComplete(successes, failures)
            }
        }
    }

    fun addCustomRule(domain: String, source: RuleSource, match: RuleMatch = RuleMatch.EXACT, onComplete: (Boolean) -> Unit) {
        val normalized = DomainName.normalize(domain)
        if (normalized == null) {
            onComplete(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val ok = ruleRepository.addCustomRule(normalized, source, match)
            loadRules()
            withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }

    fun deleteCustomRule(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ruleRepository.deleteCustomRule(id)
            loadRules()
        }
    }

    fun importRules(text: String, onComplete: (count: Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val count = ruleRepository.importCustomRules(text)
            loadRules()
            withContext(Dispatchers.Main) { onComplete(count) }
        }
    }

    fun exportRules(): String {
        return ruleRepository.exportCustomRules()
    }
}
