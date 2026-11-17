package com.example.privacyshield.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.privacyshield.filtering.RuleMatch
import com.example.privacyshield.filtering.RuleSource
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

data class CustomRule(
    val id: String,
    val domain: String,
    val source: RuleSource,
    val match: RuleMatch,
    val createdAt: Long
)

data class Blocklist(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
    val ruleCount: Int,
    val lastUpdated: Long
)

data class AppPolicy(
    val packageName: String,
    val appName: String,
    val isFiltered: Boolean
)

data class FilterEvent(
    val id: Long = 0,
    val timestamp: Long,
    val domain: String,
    val isBlocked: Boolean,
    val ruleId: String? = null
)

data class ProtectionStats(
    val totalQueries: Long,
    val blockedQueries: Long,
    val allowedQueries: Long,
    val uptimeMillis: Long = 0
)

class DatabaseHelper(private val context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "privacy_shield.db"
        const val DATABASE_VERSION = 4

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }

        val DEFAULT_STARTER_DOMAINS = listOf(
            "trafficjunky.com",
            "trafficjunky.net",
            "delivery.trafficjunky.net",
            "ads.trafficjunky.net",
            "media.trafficjunky.com",
            "exoclick.com",
            "ads.exoclick.com",
            "syndication.exoclick.com",
            "juicyads.com",
            "ad.juicyads.com",
            "eroadvertising.com",
            "popads.net",
            "serve.popads.net",
            "popcash.net",
            "adsterra.com",
            "propellerads.com",
            "propu.sh",
            "clickadu.com",
            "hilltopads.com",
            "hilltopads.net",
            "trafficfactory.biz",
            "trafficstars.com",
            "plugrush.com",
            "adxpansion.com",
            "tsyndicate.com",
            "creative.tsyndicate.com",
            "et-code.com",
            "hubtraffic.com",
            "mgid.com",
            "realsrv.com",
            "stripchatads.com",
            "chaturbateads.com",
            "crakrevenue.com",
            "bngpt.com",
            "awmtracker.com",
            "rtmark.net",
            "trafficforce.com",
            "adcash.com",
            "zeropark.com",
            "admaven.com",
            "doubleclick.net",
            "google-analytics.com",
            "analytics.google.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "admob.com",
            "googletagservices.com",
            "googlesyndication.com",
            "googleadservices.com",
            "googleads.g.doubleclick.net",
            "stats.g.doubleclick.net",
            "app-measurement.com",
            "2mdn.net",
            "aax.amazon-adsystem.com",
            "c.amazon-adsystem.com",
            "s.amazon-adsystem.com",
            "fls-na.amazon.com",
            "assoc-amazon.com",
            "ads.msn.com",
            "bat.bing.com",
            "c.bing.com",
            "advertising.com",
            "adtechus.com",
            "gemini.yahoo.com",
            "ads.facebook.com",
            "telemetry.facebook.com",
            "connect.facebook.net",
            "pixel.facebook.com",
            "an.facebook.com",
            "analytics.tiktok.com",
            "ads.tiktok.com",
            "ads.twitter.com",
            "analytics.twitter.com",
            "ads-twitter.com",
            "tr.snapchat.com",
            "ads.snapchat.com",
            "ct.pinterest.com",
            "ads.pinterest.com",
            "snap.licdn.com",
            "px.ads.linkedin.com",
            "alb.reddit.com",
            "adnxs.com",
            "ib.adnxs.com",
            "pubmatic.com",
            "ads.pubmatic.com",
            "openx.net",
            "servedbyopenx.com",
            "rubiconproject.com",
            "magnite.com",
            "casalemedia.com",
            "indexexchange.com",
            "smartadserver.com",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "trc.taboola.com",
            "outbrain.com",
            "traffic.outbrain.com",
            "sovrn.com",
            "lijit.com",
            "triplelift.com",
            "sharethrough.com",
            "bidswitch.net",
            "media.net",
            "teads.tv",
            "yieldmo.com",
            "rlcdn.com",
            "liveramp.com",
            "adsrvr.org",
            "quantserve.com",
            "quantcount.com",
            "moatads.com",
            "revcontent.com",
            "content.ad",
            "adblade.com",
            "infolinks.com",
            "exponential.com",
            "undertone.com",
            "sonobi.com",
            "gumgum.com",
            "kargo.com",
            "nativo.com",
            "appsflyer.com",
            "adjust.com",
            "adjust.net.in",
            "flurry.com",
            "data.flurry.com",
            "crashlytics.com",
            "unityads.unity3d.com",
            "unityads.unity.com",
            "applovin.com",
            "applvn.com",
            "al.applvn.com",
            "ironsrc.com",
            "supersonicads.com",
            "vungle.com",
            "ads.vungle.com",
            "chartboost.com",
            "da.chartboost.com",
            "inmobi.com",
            "adcolony.com",
            "tapjoy.com",
            "tapjoyads.com",
            "fyber.com",
            "mintegral.net",
            "pangle-ads.com",
            "pangolin-sdk-toutiao.com",
            "kochava.com",
            "control.kochava.com",
            "branch.io",
            "app.link",
            "liftoff.io",
            "smaato.net",
            "ogury.io",
            "startappservice.com",
            "startapp.com",
            "hyprmx.com",
            "kidoz.net",
            "singular.net",
            "hotjar.com",
            "static.hotjar.com",
            "clarity.ms",
            "c.clarity.ms",
            "mixpanel.com",
            "api.mixpanel.com",
            "segment.io",
            "api.segment.io",
            "amplitude.com",
            "api.amplitude.com",
            "scorecardresearch.com",
            "crazyegg.com",
            "mouseflow.com",
            "fullstory.com",
            "smartlook.com",
            "logrocket.com",
            "voicefive.com",
            "nr-data.net",
            "onesignal.com",
            "clevertap.com",
            "braze.com",
            "appboy.com",
            "leanplum.com",
            "moengage.com",
            "richads.com",
            "pushground.com",
            "pushwoosh.com",
            "kadam.net",
            "an.yandex.ru",
            "mc.yandex.ru",
            "adriver.ru",
            "top-fwz1.mail.ru",
            "target.my.com",
            "tracking.miui.com",
            "data.mistat.xiaomi.com",
            "ad.xiaomi.com",
            "samsungads.com",
            "smetrics.samsung.com",
            "metrics.data.hicloud.com",
            "ads.huawei.com"
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE custom_rules (
                id TEXT PRIMARY KEY,
                domain TEXT NOT NULL,
                source TEXT NOT NULL,
                match_type TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE blocklists (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                url TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                rule_count INTEGER NOT NULL DEFAULT 0,
                last_updated INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE subscription_rules (
                id TEXT PRIMARY KEY,
                blocklist_id TEXT NOT NULL,
                domain TEXT NOT NULL,
                match_type TEXT NOT NULL,
                FOREIGN KEY (blocklist_id) REFERENCES blocklists(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE app_policies (
                package_name TEXT PRIMARY KEY,
                app_name TEXT NOT NULL,
                is_bypassed INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE filter_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                timestamp INTEGER NOT NULL,
                domain TEXT NOT NULL,
                is_blocked INTEGER NOT NULL,
                rule_id TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE statistics (
                id INTEGER PRIMARY KEY,
                total_queries INTEGER NOT NULL DEFAULT 0,
                blocked_queries INTEGER NOT NULL DEFAULT 0,
                allowed_queries INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )

        createIndices(db)

        db.execSQL("INSERT INTO statistics (id, total_queries, blocked_queries, allowed_queries) VALUES (1, 0, 0, 0)")

        seedDefaultBlocklists(db)
        seedStarterCustomRules(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        createIndices(db)
        ensureStarterCustomRulesSeeded(db)
    }

    private fun createIndices(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_filter_events_ts ON filter_events(timestamp DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_subscription_rules_bl ON subscription_rules(blocklist_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_custom_rules_domain ON custom_rules(domain)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            seedDefaultBlocklists(db)
            seedStarterCustomRules(db)
            updateSubscriptionStarterRules(db)
            setSettingsValue(db, "seeded_starter_domains_version", "4")
        }
    }

    fun seedStarterCustomRules(db: SQLiteDatabase) {
        val now = System.currentTimeMillis()
        val stmt = db.compileStatement("INSERT OR IGNORE INTO custom_rules (id, domain, source, match_type, created_at) VALUES (?, ?, ?, ?, ?)")
        for (domain in DEFAULT_STARTER_DOMAINS) {
            stmt.bindString(1, "starter-block:$domain")
            stmt.bindString(2, domain)
            stmt.bindString(3, RuleSource.USER_BLOCK.name)
            stmt.bindString(4, RuleMatch.INCLUDE_SUBDOMAINS.name)
            stmt.bindLong(5, now)
            stmt.executeInsert()
            stmt.clearBindings()
        }
    }

    private fun ensureStarterCustomRulesSeeded(db: SQLiteDatabase) {
        val currentSeededVersion = getSettingsValue(db, "seeded_starter_domains_version")?.toIntOrNull() ?: 0
        if (currentSeededVersion < 4) {
            seedDefaultBlocklists(db)
            seedStarterCustomRules(db)
            updateSubscriptionStarterRules(db)
            setSettingsValue(db, "seeded_starter_domains_version", "4")
        }
    }

    private fun getSettingsValue(db: SQLiteDatabase, key: String): String? {
        val cursor = db.query("settings", arrayOf("value"), "key = ?", arrayOf(key), null, null, null)
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun setSettingsValue(db: SQLiteDatabase, key: String, value: String) {
        val cv = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun updateSubscriptionStarterRules(db: SQLiteDatabase) {
        val blocklistId = "starter-privacy-list"
        var loadedCount = 0

        val hasAsset = try {
            context.assets.open("starter_hosts.txt.gz").use { inputStream ->
                GZIPInputStream(inputStream).use { gz ->
                    BufferedReader(InputStreamReader(gz, Charsets.UTF_8)).useLines { lines ->
                        val stmt = db.compileStatement("INSERT OR IGNORE INTO subscription_rules (id, blocklist_id, domain, match_type) VALUES (?, ?, ?, ?)")
                        for (line in lines) {
                            val domain = line.trim()
                            if (domain.isNotEmpty()) {
                                stmt.bindString(1, "$blocklistId:$domain")
                                stmt.bindString(2, blocklistId)
                                stmt.bindString(3, domain)
                                stmt.bindString(4, RuleMatch.INCLUDE_SUBDOMAINS.name)
                                stmt.executeInsert()
                                stmt.clearBindings()
                                loadedCount++
                            }
                        }
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }

        if (!hasAsset || loadedCount == 0) {
            val stmt = db.compileStatement("INSERT OR IGNORE INTO subscription_rules (id, blocklist_id, domain, match_type) VALUES (?, ?, ?, ?)")
            for (domain in DEFAULT_STARTER_DOMAINS) {
                stmt.bindString(1, "$blocklistId:$domain")
                stmt.bindString(2, blocklistId)
                stmt.bindString(3, domain)
                stmt.bindString(4, RuleMatch.INCLUDE_SUBDOMAINS.name)
                stmt.executeInsert()
                stmt.clearBindings()
                loadedCount++
            }
        }

        val cvUpdate = ContentValues().apply {
            put("rule_count", loadedCount)
        }
        db.update("blocklists", cvUpdate, "id = ?", arrayOf(blocklistId))
    }

    private fun seedDefaultBlocklists(db: SQLiteDatabase) {
        val starterLists = listOf(
            Blocklist(
                id = "starter-privacy-list",
                name = "StevenBlack Unified (Ads & Malware)",
                url = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
                enabled = true,
                ruleCount = DEFAULT_STARTER_DOMAINS.size,
                lastUpdated = System.currentTimeMillis()
            ),
            Blocklist(
                id = "adguard-dns-list",
                name = "AdGuard DNS Filter",
                url = "https://adguardteam.github.io/HostlistsRegistry/assets/filter_1.txt",
                enabled = true,
                ruleCount = 0,
                lastUpdated = System.currentTimeMillis()
            ),
            Blocklist(
                id = "peter-lowe-list",
                name = "Peter Lowe's Ad Tracking",
                url = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext",
                enabled = true,
                ruleCount = 0,
                lastUpdated = System.currentTimeMillis()
            )
        )

        for (list in starterLists) {
            val cv = ContentValues().apply {
                put("id", list.id)
                put("name", list.name)
                put("url", list.url)
                put("enabled", if (list.enabled) 1 else 0)
                put("rule_count", list.ruleCount)
                put("last_updated", list.lastUpdated)
            }
            db.insertWithOnConflict("blocklists", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        }

        updateSubscriptionStarterRules(db)
    }
}
