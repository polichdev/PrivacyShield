package com.example.privacyshield

import android.content.Intent
import android.content.Context
import java.util.Locale
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.core.view.WindowCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.privacyshield.data.AppPolicyRepository
import com.example.privacyshield.data.DatabaseHelper
import com.example.privacyshield.data.RuleRepository
import com.example.privacyshield.data.SettingsRepository
import com.example.privacyshield.data.StatisticsRepository
import com.example.privacyshield.ui.screens.ApplicationsScreen
import com.example.privacyshield.ui.screens.DashboardScreen
import com.example.privacyshield.ui.screens.FiltersScreen
import com.example.privacyshield.ui.screens.PlayerScreen
import com.example.privacyshield.ui.screens.SettingsScreen
import com.example.privacyshield.ui.viewmodels.DashboardViewModel
import com.example.privacyshield.ui.viewmodels.FiltersViewModel
import com.example.privacyshield.ui.theme.ClaudeBackground
import com.example.privacyshield.ui.theme.ClaudeCard
import com.example.privacyshield.ui.theme.ClaudeIvory
import com.example.privacyshield.ui.theme.ClaudeSurface
import com.example.privacyshield.ui.theme.ClaudeTerracotta
import com.example.privacyshield.ui.theme.ClaudeTextSecondary
import com.example.privacyshield.ui.theme.PrivacyShieldTheme
import com.example.privacyshield.youtube.YouTubeAudioService
import com.example.privacyshield.youtube.YouTubeUrlParser

enum class NavTab {
    DASHBOARD, APPS, FILTERS, PLAYER, SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var ruleRepository: RuleRepository
    private lateinit var appPolicyRepository: AppPolicyRepository
    private lateinit var statisticsRepository: StatisticsRepository
    private lateinit var settingsRepository: SettingsRepository

    private val currentTabState = mutableStateOf(NavTab.DASHBOARD)

    override fun attachBaseContext(base: Context) {
        val code = base.getSharedPreferences("appearance", 0).getString("language", "en") ?: "en"
        val locale = Locale.forLanguageTag(code)
        Locale.setDefault(locale)
        val config = android.content.res.Configuration(base.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(base.createConfigurationContext(config))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("selected_tab", currentTabState.value.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedTabName = savedInstanceState?.getString("selected_tab")
            ?: getSharedPreferences("appearance", 0).getString("last_tab", null)
        if (savedTabName != null) {
            try {
                currentTabState.value = NavTab.valueOf(savedTabName)
            } catch (_: Exception) {}
        }
        @Suppress("DEPRECATION")
        window.statusBarColor = android.graphics.Color.parseColor(if (getSharedPreferences("appearance", 0).getBoolean("light", false)) "#FFF8F3" else "#0F0F12")
        window.navigationBarColor = android.graphics.Color.parseColor(if (getSharedPreferences("appearance", 0).getBoolean("light", false)) "#FFFFFF" else "#16161B")
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = getSharedPreferences("appearance", 0).getBoolean("light", false)
        insetsController.isAppearanceLightNavigationBars = getSharedPreferences("appearance", 0).getBoolean("light", false)

        dbHelper = DatabaseHelper.getInstance(this)
        ruleRepository = RuleRepository(dbHelper)
        appPolicyRepository = AppPolicyRepository(dbHelper)
        statisticsRepository = StatisticsRepository(dbHelper)
        settingsRepository = SettingsRepository(dbHelper)

        val dashboardViewModel = DashboardViewModel(statisticsRepository, ruleRepository)
        val filtersViewModel = FiltersViewModel(ruleRepository)

        setContent {
            PrivacyShieldTheme(light = getSharedPreferences("appearance", 0).getBoolean("light", false)) {
                val currentTab by currentTabState
                MainAppScreen(
                    selectedTab = currentTab,
                    onTabSelect = { tab ->
                        currentTabState.value = tab
                        getSharedPreferences("appearance", 0).edit().putString("last_tab", tab.name).apply()
                    },
                    dashboardViewModel = dashboardViewModel,
                    filtersViewModel = filtersViewModel,
                    appPolicyRepository = appPolicyRepository,
                    settingsRepository = settingsRepository
                )
            }
        }

        checkNotificationPermission()
        handleIncomingIntent(intent)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("open_player", false) == true) currentTabState.value = NavTab.PLAYER
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val parsed = YouTubeUrlParser.parse(sharedText)
            if (parsed != null) {
                currentTabState.value = NavTab.PLAYER
                YouTubeAudioService.enqueue(this, parsed.videoId, parsed.startSeconds)
                Toast.makeText(this, getString(R.string.player_toast_added_to_queue), Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainAppScreen(
    selectedTab: NavTab,
    onTabSelect: (NavTab) -> Unit,
    dashboardViewModel: DashboardViewModel,
    filtersViewModel: FiltersViewModel,
    appPolicyRepository: AppPolicyRepository,
    settingsRepository: SettingsRepository
) {
    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = com.example.privacyshield.ui.theme.OrangePrimary,
                    selectedTextColor = com.example.privacyshield.ui.theme.OrangePrimary,
                    unselectedIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = androidx.compose.ui.graphics.Color(0xFF2A1B10)
                )

                NavigationBarItem(
                    selected = selectedTab == NavTab.DASHBOARD,
                    onClick = { onTabSelect(NavTab.DASHBOARD) },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_dashboard),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_shield),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == NavTab.APPS,
                    onClick = { onTabSelect(NavTab.APPS) },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_apps),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_apps),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == NavTab.FILTERS,
                    onClick = { onTabSelect(NavTab.FILTERS) },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_filters),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_filters),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == NavTab.PLAYER,
                    onClick = { onTabSelect(NavTab.PLAYER) },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_player),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_player),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = itemColors
                )
                NavigationBarItem(
                    selected = selectedTab == NavTab.SETTINGS,
                    onClick = { onTabSelect(NavTab.SETTINGS) },
                    label = {
                        Text(
                            text = stringResource(R.string.tab_settings),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    icon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_nav_settings),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    colors = itemColors
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            Crossfade(
                targetState = selectedTab,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "TabTransition"
            ) { tab ->
                when (tab) {
                    NavTab.DASHBOARD -> DashboardScreen(
                        viewModel = dashboardViewModel
                    )
                    NavTab.APPS -> ApplicationsScreen(
                        appPolicyRepository = appPolicyRepository
                    )
                    NavTab.FILTERS -> FiltersScreen(
                        viewModel = filtersViewModel
                    )
                    NavTab.PLAYER -> PlayerScreen()
                    NavTab.SETTINGS -> SettingsScreen(
                        settingsRepository = settingsRepository
                    )
                }
            }
        }
    }
}

