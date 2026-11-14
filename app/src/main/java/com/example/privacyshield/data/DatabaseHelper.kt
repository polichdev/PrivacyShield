package com.example.privacyshield.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.privacyshield.filtering.DomainRule
import com.example.privacyshield.filtering.RuleMatch
import com.example.privacyshield.filtering.RuleSource

data class CustomRule(
    val id: String,
    val domain: String,
    val source: RuleSource,
    val match: RuleMatch,
    val createdAt: Long = System.currentTimeMillis()
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
    val ruleId: String?
)

data class ProtectionStats(
    val totalQueries: Long,
    val blockedQueries: Long,
    val allowedQueries: Long,
    val uptimeMillis: Long = 0
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "privacy_shield.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: DatabaseHelper? = null

        fun getInstance(context: Context): DatabaseHelper {
            return instance ?: synchronized(this) {
                instance ?: DatabaseHelper(context.applicationContext).also { instance = it }
            }
        }

        val DEFAULT_STARTER_DOMAINS = listOf(
            "doubleclick.net",
            "google-analytics.com",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "admob.com",
            "ads.facebook.com",
            "telemetry.facebook.com",
            "ads.twitter.com",
            "scorecardresearch.com",
            "appsflyer.com",
            "adjust.com",
            "flurry.com",
            "crashlytics.com",
            "unityads.unity3d.com",
            "applovin.com",
            "ironsrc.com",
            "vungle.com",
            "chartboost.com",
            "inmobi.com",
            "taboola.com",
            "outbrain.com",
            "criteo.com",
            "adnxs.com",
            "quantserve.com",
            "branch.io"
        )
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
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
                enabled INTEGER NOT NULL,
                rule_count INTEGER NOT NULL,
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
                match_type TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE app_policies (
                package_name TEXT PRIMARY KEY,
                app_name TEXT NOT NULL,
                is_filtered INTEGER NOT NULL
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
            CREATE TABLE statistics (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                total_queries INTEGER NOT NULL,
                blocked_queries INTEGER NOT NULL,
                allowed_queries INTEGER NOT NULL
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

        createIndices(db)

        db.execSQL("INSERT INTO statistics (id, total_queries, blocked_queries, allowed_queries) VALUES (1, 0, 0, 0)")

        seedDefaultBlocklist(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        createIndices(db)
    }

    private fun createIndices(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_filter_events_ts ON filter_events(timestamp DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_subscription_rules_bl ON subscription_rules(blocklist_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_custom_rules_domain ON custom_rules(domain)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }

    private fun seedDefaultBlocklist(db: SQLiteDatabase) {
        val blocklistId = "starter-privacy-list"
        val starterList = Blocklist(
            id = blocklistId,
            name = "Default Tracking & Ad Protection",
            url = "https://raw.githubusercontent.com/privacy-shield/lists/main/starter.txt",
            enabled = true,
            ruleCount = DEFAULT_STARTER_DOMAINS.size,
            lastUpdated = System.currentTimeMillis()
        )

        val cvList = ContentValues().apply {
            put("id", starterList.id)
            put("name", starterList.name)
            put("url", starterList.url)
            put("enabled", if (starterList.enabled) 1 else 0)
            put("rule_count", starterList.ruleCount)
            put("last_updated", starterList.lastUpdated)
        }
        db.insert("blocklists", null, cvList)

        for (domain in DEFAULT_STARTER_DOMAINS) {
            val cvRule = ContentValues().apply {
                put("id", "$blocklistId:$domain")
                put("blocklist_id", blocklistId)
                put("domain", domain)
                put("match_type", RuleMatch.INCLUDE_SUBDOMAINS.name)
            }
            db.insert("subscription_rules", null, cvRule)
        }
    }
}

