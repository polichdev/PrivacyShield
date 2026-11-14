package com.example.privacyshield.data

import android.content.ContentValues
import android.content.Context
import com.example.privacyshield.filtering.DomainName
import com.example.privacyshield.filtering.DomainRule
import com.example.privacyshield.filtering.DomainRuleMatcher
import com.example.privacyshield.filtering.RuleMatch
import com.example.privacyshield.filtering.RuleSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class RuleRepository(private val dbHelper: DatabaseHelper) {

    private val _rulesVersion = MutableStateFlow(0)
    val rulesVersion: StateFlow<Int> = _rulesVersion.asStateFlow()

    @Synchronized
    fun getCustomRules(): List<CustomRule> {
        val rules = mutableListOf<CustomRule>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "custom_rules",
            arrayOf("id", "domain", "source", "match_type", "created_at"),
            null, null, null, null, "created_at DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                rules.add(
                    CustomRule(
                        id = it.getString(0),
                        domain = it.getString(1),
                        source = RuleSource.valueOf(it.getString(2)),
                        match = RuleMatch.valueOf(it.getString(3)),
                        createdAt = it.getLong(4)
                    )
                )
            }
        }
        return rules
    }

    @Synchronized
    fun addCustomRule(domain: String, source: RuleSource, match: RuleMatch = RuleMatch.INCLUDE_SUBDOMAINS): Boolean {
        val normalized = DomainName.normalize(domain) ?: return false
        val id = UUID.randomUUID().toString()
        val cv = ContentValues().apply {
            put("id", id)
            put("domain", normalized)
            put("source", source.name)
            put("match_type", match.name)
            put("created_at", System.currentTimeMillis())
        }
        val db = dbHelper.writableDatabase
        val result = db.insertWithOnConflict("custom_rules", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        if (result != -1L) {
            _rulesVersion.value += 1
            return true
        }
        return false
    }

    @Synchronized
    fun deleteCustomRule(id: String): Boolean {
        val db = dbHelper.writableDatabase
        val deleted = db.delete("custom_rules", "id = ?", arrayOf(id))
        if (deleted > 0) {
            _rulesVersion.value += 1
            return true
        }
        return false
    }

    @Synchronized
    fun getBlocklists(): List<Blocklist> {
        val lists = mutableListOf<Blocklist>()
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            "blocklists",
            arrayOf("id", "name", "url", "enabled", "rule_count", "last_updated"),
            null, null, null, null, "name ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                lists.add(
                    Blocklist(
                        id = it.getString(0),
                        name = it.getString(1),
                        url = it.getString(2),
                        enabled = it.getInt(3) == 1,
                        ruleCount = it.getInt(4),
                        lastUpdated = it.getLong(5)
                    )
                )
            }
        }
        return lists
    }

    @Synchronized
    fun toggleBlocklist(id: String, enabled: Boolean): Boolean {
        val cv = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
        }
        val db = dbHelper.writableDatabase
        val updated = db.update("blocklists", cv, "id = ?", arrayOf(id))
        if (updated > 0) {
            _rulesVersion.value += 1
            return true
        }
        return false
    }

    companion object {
        fun isValidBlocklistUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return false
            return try {
                val parsed = java.net.URL(trimmed)
                parsed.host.isNotBlank() && !parsed.host.contains(" ")
            } catch (_: Exception) {
                false
            }
        }
    }

    @Synchronized
    fun addBlocklist(name: String, url: String): String? {
        val trimmedName = name.trim()
        val trimmedUrl = url.trim()
        if (trimmedName.isEmpty() || !isValidBlocklistUrl(trimmedUrl)) return null

        val id = UUID.randomUUID().toString()
        val cv = ContentValues().apply {
            put("id", id)
            put("name", trimmedName)
            put("url", trimmedUrl)
            put("enabled", 1)
            put("rule_count", 0)
            put("last_updated", System.currentTimeMillis())
        }
        val db = dbHelper.writableDatabase
        val result = db.insert("blocklists", null, cv)
        if (result != -1L) {
            _rulesVersion.value += 1
            return id
        }
        return null
    }

    @Synchronized
    fun deleteBlocklist(id: String): Boolean {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("subscription_rules", "blocklist_id = ?", arrayOf(id))
            val deleted = db.delete("blocklists", "id = ?", arrayOf(id))
            db.setTransactionSuccessful()
            if (deleted > 0) {
                _rulesVersion.value += 1
                return true
            }
        } finally {
            db.endTransaction()
        }
        return false
    }

    @Synchronized
    fun updateBlocklistRules(id: String, domains: List<String>): Boolean {
        if (domains.isEmpty()) return false
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {

            db.delete("subscription_rules", "blocklist_id = ?", arrayOf(id))

            for (domain in domains) {
                val cvRule = ContentValues().apply {
                    put("id", "$id:$domain")
                    put("blocklist_id", id)
                    put("domain", domain)
                    put("match_type", RuleMatch.INCLUDE_SUBDOMAINS.name)
                }
                db.insert("subscription_rules", null, cvRule)
            }

            val cvList = ContentValues().apply {
                put("rule_count", domains.size)
                put("last_updated", System.currentTimeMillis())
            }
            db.update("blocklists", cvList, "id = ?", arrayOf(id))

            db.setTransactionSuccessful()
            _rulesVersion.value += 1
            return true
        } finally {
            db.endTransaction()
        }
    }

    suspend fun downloadAndRefreshBlocklist(id: String): Boolean {
        val blocklist = getBlocklists().firstOrNull { it.id == id } ?: return false
        return try {
            val url = java.net.URL(blocklist.url)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 8000
            connection.readTimeout = 10000
            connection.requestMethod = "GET"

            if (connection.responseCode in 200..299) {
                val domains = com.example.privacyshield.filtering.HostsParser.parse(connection.inputStream)
                if (domains.isNotEmpty()) {
                    updateBlocklistRules(id, domains)
                } else false
            } else false
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun exportCustomRules(): String {
        val rules = getCustomRules()
        val sb = java.lang.StringBuilder()
        for (rule in rules) {
            sb.append("${rule.source.name},${rule.match.name},${rule.domain}\n")
        }
        return sb.toString()
    }

    @Synchronized
    fun importCustomRules(data: String): Int {
        var count = 0
        val lines = data.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith('#')) continue
            val parts = trimmed.split(',')
            if (parts.size >= 3) {
                val source = try { RuleSource.valueOf(parts[0].trim()) } catch (_: Exception) { null }
                val match = try { RuleMatch.valueOf(parts[1].trim()) } catch (_: Exception) { null }
                val domain = parts[2].trim()
                if (source != null && match != null) {
                    if (addCustomRule(domain, source, match)) {
                        count++
                    }
                }
            }
        }
        return count
    }

    @Volatile
    private var cachedMatcher: DomainRuleMatcher? = null
    private var lastCompiledVersion: Int = -1

    @Synchronized
    fun getCompiledMatcher(): DomainRuleMatcher {
        val currentVersion = _rulesVersion.value
        val existing = cachedMatcher
        if (existing != null && lastCompiledVersion == currentVersion) {
            return existing
        }

        val domainRules = mutableListOf<DomainRule>()
        val db = dbHelper.readableDatabase

        val cursorCustom = db.query("custom_rules", arrayOf("id", "domain", "source", "match_type"), null, null, null, null, null)
        cursorCustom.use {
            while (it.moveToNext()) {
                val domain = it.getString(1)
                val normalized = DomainName.normalize(domain)
                if (normalized != null) {
                    domainRules.add(
                        DomainRule(
                            id = it.getString(0),
                            domain = normalized,
                            source = RuleSource.valueOf(it.getString(2)),
                            match = RuleMatch.valueOf(it.getString(3))
                        )
                    )
                }
            }
        }

        val querySub = """
            SELECT sr.id, sr.domain, sr.match_type
            FROM subscription_rules sr
            JOIN blocklists bl ON sr.blocklist_id = bl.id
            WHERE bl.enabled = 1
        """.trimIndent()
        val cursorSub = db.rawQuery(querySub, null)
        cursorSub.use {
            while (it.moveToNext()) {
                val domain = it.getString(1)
                val normalized = DomainName.normalize(domain)
                if (normalized != null) {
                    domainRules.add(
                        DomainRule(
                            id = it.getString(0),
                            domain = normalized,
                            source = RuleSource.SUBSCRIPTION,
                            match = RuleMatch.valueOf(it.getString(2))
                        )
                    )
                }
            }
        }

        val systemWhitelist = listOf(
            DomainRule("sys-allow-moo", "yt.chocolatemoo53.com", RuleSource.USER_ALLOW, RuleMatch.INCLUDE_SUBDOMAINS),
            DomainRule("sys-allow-yt", "googlevideo.com", RuleSource.USER_ALLOW, RuleMatch.INCLUDE_SUBDOMAINS),
            DomainRule("sys-allow-ytimg", "ytimg.com", RuleSource.USER_ALLOW, RuleMatch.INCLUDE_SUBDOMAINS),
            DomainRule("sys-allow-youtube", "youtube.com", RuleSource.USER_ALLOW, RuleMatch.INCLUDE_SUBDOMAINS)
        )
        domainRules.addAll(systemWhitelist)

        val matcher = DomainRuleMatcher(domainRules)
        cachedMatcher = matcher
        lastCompiledVersion = currentVersion
        return matcher
    }
}

class AppPolicyRepository(private val dbHelper: DatabaseHelper) {

    private val _policiesVersion = MutableStateFlow(0)
    val policiesVersion: StateFlow<Int> = _policiesVersion.asStateFlow()

    @Synchronized
    fun getAppPolicies(): List<AppPolicy> {
        val policies = mutableListOf<AppPolicy>()
        val db = dbHelper.readableDatabase
        val cursor = db.query("app_policies", arrayOf("package_name", "app_name", "is_filtered"), null, null, null, null, "app_name ASC")
        cursor.use {
            while (it.moveToNext()) {
                policies.add(
                    AppPolicy(
                        packageName = it.getString(0),
                        appName = it.getString(1),
                        isFiltered = it.getInt(2) == 1
                    )
                )
            }
        }
        return policies
    }

    @Synchronized
    fun setAppFiltered(packageName: String, appName: String, isFiltered: Boolean) {
        val cv = ContentValues().apply {
            put("package_name", packageName)
            put("app_name", appName)
            put("is_filtered", if (isFiltered) 1 else 0)
        }
        val db = dbHelper.writableDatabase
        db.insertWithOnConflict("app_policies", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
        _policiesVersion.value += 1
    }

    @Synchronized
    fun getFilteredPackages(): Set<String> {
        val set = mutableSetOf<String>()
        val db = dbHelper.readableDatabase
        val cursor = db.query("app_policies", arrayOf("package_name"), "is_filtered = 1", null, null, null, null)
        cursor.use {
            while (it.moveToNext()) {
                set.add(it.getString(0))
            }
        }
        return set
    }
}

class StatisticsRepository(private val dbHelper: DatabaseHelper) {

    private val _statsState = MutableStateFlow(getStats())
    val statsState: StateFlow<ProtectionStats> = _statsState.asStateFlow()

    @Synchronized
    fun getStats(): ProtectionStats {
        val db = dbHelper.readableDatabase
        val cursor = db.query("statistics", arrayOf("total_queries", "blocked_queries", "allowed_queries"), "id = 1", null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return ProtectionStats(
                    totalQueries = it.getLong(0),
                    blockedQueries = it.getLong(1),
                    allowedQueries = it.getLong(2)
                )
            }
        }
        return ProtectionStats(0, 0, 0)
    }

    private var eventCount = 0

    @Synchronized
    fun recordEvent(domain: String, isBlocked: Boolean, ruleId: String?) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val cvEvent = ContentValues().apply {
                put("timestamp", System.currentTimeMillis())
                put("domain", domain)
                put("is_blocked", if (isBlocked) 1 else 0)
                put("rule_id", ruleId)
            }
            db.insert("filter_events", null, cvEvent)

            val updateSql = if (isBlocked) {
                "UPDATE statistics SET total_queries = total_queries + 1, blocked_queries = blocked_queries + 1 WHERE id = 1"
            } else {
                "UPDATE statistics SET total_queries = total_queries + 1, allowed_queries = allowed_queries + 1 WHERE id = 1"
            }
            db.execSQL(updateSql)

            eventCount++

            if (eventCount % 50 == 0) {
                db.execSQL("DELETE FROM filter_events WHERE id NOT IN (SELECT id FROM filter_events ORDER BY id DESC LIMIT 200)")
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        _statsState.update { current ->
            ProtectionStats(
                totalQueries = current.totalQueries + 1,
                blockedQueries = if (isBlocked) current.blockedQueries + 1 else current.blockedQueries,
                allowedQueries = if (!isBlocked) current.allowedQueries + 1 else current.allowedQueries,
                uptimeMillis = current.uptimeMillis
            )
        }
    }

    @Synchronized
    fun getRecentEvents(limit: Int = 50): List<FilterEvent> {
        val events = mutableListOf<FilterEvent>()
        val db = dbHelper.readableDatabase
        val cursor = db.query("filter_events", arrayOf("id", "timestamp", "domain", "is_blocked", "rule_id"), null, null, null, null, "id DESC", limit.toString())
        cursor.use {
            while (it.moveToNext()) {
                events.add(
                    FilterEvent(
                        id = it.getLong(0),
                        timestamp = it.getLong(1),
                        domain = it.getString(2),
                        isBlocked = it.getInt(3) == 1,
                        ruleId = it.getString(4)
                    )
                )
            }
        }
        return events
    }

    @Synchronized
    fun getTopBlockedDomains(limit: Int = 10): List<Pair<String, Int>> {
        val list = mutableListOf<Pair<String, Int>>()
        val db = dbHelper.readableDatabase
        val query = """
            SELECT domain, COUNT(*) as cnt
            FROM filter_events
            WHERE is_blocked = 1
            GROUP BY domain
            ORDER BY cnt DESC
            LIMIT ?
        """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf(limit.toString()))
        cursor.use {
            while (it.moveToNext()) {
                list.add(Pair(it.getString(0), it.getInt(1)))
            }
        }
        return list
    }

    @Synchronized
    fun resetStats() {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE statistics SET total_queries = 0, blocked_queries = 0, allowed_queries = 0 WHERE id = 1")
            db.execSQL("DELETE FROM filter_events")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        _statsState.value = ProtectionStats(0, 0, 0)
    }
}

class SettingsRepository(private val dbHelper: DatabaseHelper) {

    companion object {
        const val KEY_UPSTREAM_DNS = "upstream_dns"
        const val DEFAULT_UPSTREAM_DNS = "1.1.1.1"
    }

    @Synchronized
    fun getUpstreamDns(): String {
        val db = dbHelper.readableDatabase
        val cursor = db.query("settings", arrayOf("value"), "key = ?", arrayOf(KEY_UPSTREAM_DNS), null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return DEFAULT_UPSTREAM_DNS
    }

    @Synchronized
    fun setUpstreamDns(ip: String) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("key", KEY_UPSTREAM_DNS)
            put("value", ip.trim())
        }
        db.insertWithOnConflict("settings", null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
    }
}

