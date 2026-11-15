package com.example.privacyshield.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.privacyshield.R
import com.example.privacyshield.data.SettingsRepository
import com.example.privacyshield.ui.theme.OrangePrimary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsRepository: SettingsRepository, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("appearance", 0) }
    var tab by remember { mutableIntStateOf(-1) }
    BackHandler(enabled = tab != -1) { tab = -1 }
    var dns by remember { mutableStateOf(settingsRepository.getUpstreamDns()) }
    var custom by remember { mutableStateOf(dns) }
    val providers = listOf("Cloudflare" to "1.1.1.1", "Quad9" to "9.9.9.9", "Google" to "8.8.8.8")
    var customSelected by remember { mutableStateOf(providers.none { it.second == dns }) }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (tab == -1) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp))
            Spacer(Modifier.height(12.dp))
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                SettingsRow("DNS", dns, "◉") { tab = 0 }
                HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                SettingsRow(
                    stringResource(R.string.settings_language),
                    stringResource(if (prefs.getString("language", "en") == "uk") R.string.lang_ukrainian else R.string.lang_english),
                    "文"
                ) { tab = 1 }
                HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
                SettingsRow(
                    stringResource(R.string.settings_appearance),
                    stringResource(if (prefs.getBoolean("light", false)) R.string.settings_theme_light else R.string.settings_theme_dark),
                    "◐"
                ) { tab = 2 }
            }
            Spacer(Modifier.height(32.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Privacy Shield v0.3.9 (Build 19)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Row(Modifier.fillMaxWidth().clickable { tab = -1 }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", style = MaterialTheme.typography.headlineMedium, color = OrangePrimary)
                Spacer(Modifier.width(16.dp))
                Text(
                    when (tab) {
                        0 -> "DNS"
                        1 -> stringResource(R.string.settings_language)
                        else -> stringResource(R.string.settings_appearance)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                -1 -> Unit
                0 -> {
                    item { Text(stringResource(R.string.settings_dns_server), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item {
                        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(8.dp)) {
                                providers.forEach { (name, ip) ->
                                    SettingChoice(name, ip, !customSelected && dns == ip) { customSelected = false; dns = ip; settingsRepository.setUpstreamDns(ip) }
                                }
                                SettingChoice(stringResource(R.string.settings_custom_server), "", customSelected) { customSelected = true }
                                if (customSelected) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = custom, onValueChange = { custom = it }, singleLine = true, modifier = Modifier.weight(1f), label = { Text("IPv4") })
                                    Button(onClick = { if (custom.isNotBlank()) { dns = custom.trim(); settingsRepository.setUpstreamDns(dns) } }) { Text(stringResource(R.string.action_save)) }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item { Text(stringResource(R.string.settings_app_language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item { Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(8.dp)) {
                        listOf("uk" to stringResource(R.string.lang_ukrainian), "en" to stringResource(R.string.lang_english)).forEach { (code, label) ->
                            SettingChoice(label, "", Locale.getDefault().language == code) {
                                prefs.edit().putString("language", code).putString("last_tab", "SETTINGS").apply()
                                (context as? Activity)?.recreate()
                            }
                        }
                    } } }
                }
                else -> {
                    item { Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                    item { Card(shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(8.dp)) {
                        val light = prefs.getBoolean("light", false)
                        SettingChoice(stringResource(R.string.settings_theme_dark), "", !light) { prefs.edit().putBoolean("light", false).putString("last_tab", "SETTINGS").apply(); (context as? Activity)?.recreate() }
                        SettingChoice(stringResource(R.string.settings_theme_light), "", light) { prefs.edit().putBoolean("light", true).putString("last_tab", "SETTINGS").apply(); (context as? Activity)?.recreate() }
                    } } }
                }
            }
        }
    }
}

@Composable
private fun SettingChoice(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = OrangePrimary))
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, symbol: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(38.dp).background(OrangePrimary.copy(alpha = .14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(symbol, color = OrangePrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

