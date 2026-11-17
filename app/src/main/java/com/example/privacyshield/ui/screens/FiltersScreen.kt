package com.example.privacyshield.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.privacyshield.R
import com.example.privacyshield.data.RuleRepository
import com.example.privacyshield.filtering.RuleSource
import com.example.privacyshield.ui.theme.OrangePrimary
import com.example.privacyshield.ui.theme.StatusGreen
import com.example.privacyshield.ui.theme.StatusRed
import com.example.privacyshield.ui.viewmodels.FiltersViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FiltersScreen(
    viewModel: FiltersViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val blocklists by viewModel.blocklists.collectAsStateWithLifecycle()
    val customRules by viewModel.customRules.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }

    var showAddBlocklistDialog by remember { mutableStateOf(false) }
    var newBlocklistName by remember { mutableStateOf("") }
    var newBlocklistUrl by remember { mutableStateOf("") }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportedText by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }

    var domainInput by remember { mutableStateOf("") }
    var domainError by remember { mutableStateOf<String?>(null) }
    val invalidDomainMessage = stringResource(R.string.error_invalid_domain)

    val allowRules = remember(customRules) { customRules.filter { it.source == RuleSource.USER_ALLOW } }
    val blockRules = remember(customRules) { customRules.filter { it.source == RuleSource.USER_BLOCK } }

    val filteredAllowRules = remember(allowRules, searchQuery) {
        if (searchQuery.isBlank()) allowRules
        else allowRules.filter { it.domain.contains(searchQuery.trim(), ignoreCase = true) }
    }

    val filteredBlockRules = remember(blockRules, searchQuery) {
        if (searchQuery.isBlank()) blockRules
        else blockRules.filter { it.domain.contains(searchQuery.trim(), ignoreCase = true) }
    }

    if (showAddBlocklistDialog) {
        AlertDialog(
            onDismissRequest = { showAddBlocklistDialog = false },
            title = { Text(stringResource(R.string.filter_add_blocklist_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newBlocklistName,
                        onValueChange = { newBlocklistName = it },
                        label = { Text(stringResource(R.string.blocklist_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newBlocklistUrl,
                        onValueChange = { newBlocklistUrl = it },
                        label = { Text(stringResource(R.string.filter_blocklist_url_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        stringResource(R.string.filter_popular_presets),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA2A2B0)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RuleRepository.PRESET_BLOCKLISTS.take(2).forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    newBlocklistName = preset.name
                                    newBlocklistUrl = preset.url
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(preset.name.substringBefore(" "), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RuleRepository.PRESET_BLOCKLISTS.drop(2).take(2).forEach { preset ->
                            OutlinedButton(
                                onClick = {
                                    newBlocklistName = preset.name
                                    newBlocklistUrl = preset.url
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(preset.name.substringBefore(" "), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newBlocklistName.trim()
                        val url = newBlocklistUrl.trim()
                        if (name.isNotEmpty() && url.isNotEmpty()) {
                            viewModel.addBlocklist(name, url) { success ->
                                if (!success) {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.filter_error_invalid_url),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                            newBlocklistName = ""
                            newBlocklistUrl = ""
                            showAddBlocklistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text(stringResource(R.string.action_save), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddBlocklistDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.action_export)) },
            text = {
                OutlinedTextField(
                    value = exportedText,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text("OK", color = Color.White)
                }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.action_import)) },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    placeholder = { Text(stringResource(R.string.import_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (importText.isNotBlank()) {
                            viewModel.importRules(importText) { count ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.filter_imported_count, count),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            importText = ""
                            showImportDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                ) {
                    Text(stringResource(R.string.action_save), color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            val tabs = listOf(
                Pair(stringResource(R.string.filter_tab_lists), blocklists.count { it.enabled }),
                Pair(stringResource(R.string.filter_tab_allow), allowRules.size),
                Pair(stringResource(R.string.filter_tab_block), blockRules.size)
            )

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = OrangePrimary
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
            ) {
                tabs.forEachIndexed { index, (label, count) ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = {
                            selectedTab = index
                            viewModel.onSearchQueryChange("")
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSelected) OrangePrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$count",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) OrangePrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }

        if (selectedTab == 0) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.refreshAllBlocklists { _, failCount ->
                                Toast.makeText(
                                    context,
                                    if (failCount == 0) context.getString(R.string.filter_lists_updated_success)
                                    else context.getString(R.string.filter_lists_updated_error, failCount),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        enabled = !isRefreshing && blocklists.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.refreshing), fontWeight = FontWeight.Bold, color = Color.White)
                        } else {
                            Text(stringResource(R.string.action_refresh), fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = { showAddBlocklistDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(stringResource(R.string.filter_btn_add_list), fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                    }
                }
            }

            items(blocklists, key = { it.id }) { bl ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = bl.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.filter_rules_count, bl.ruleCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(
                                checked = bl.enabled,
                                onCheckedChange = { viewModel.toggleBlocklist(bl.id, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = OrangePrimary
                                )
                            )
                            if (bl.id != "starter-privacy-list") {
                                IconButton(
                                    onClick = { viewModel.deleteBlocklist(bl.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_close),
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = StatusRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (selectedTab == 1 || selectedTab == 2) {
            val isAllowTab = selectedTab == 1
            val currentList = if (isAllowTab) filteredAllowRules else filteredBlockRules

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = domainInput,
                                onValueChange = {
                                    domainInput = it
                                    domainError = null
                                },
                                placeholder = {
                                    Text(
                                        if (isAllowTab) stringResource(R.string.filter_hint_allow)
                                        else stringResource(R.string.filter_hint_block),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                isError = domainError != null,
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    val targetSource = if (isAllowTab) RuleSource.USER_ALLOW else RuleSource.USER_BLOCK
                                    viewModel.addCustomRule(domainInput, targetSource) { success ->
                                        if (success) {
                                            domainInput = ""
                                            domainError = null
                                            Toast.makeText(context, context.getString(R.string.filter_rule_added_success), Toast.LENGTH_SHORT).show()
                                        } else {
                                            domainError = invalidDomainMessage
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = if (isAllowTab) StatusGreen else OrangePrimary)
                            ) {
                                Text(stringResource(R.string.filter_btn_add_domain), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (domainError != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(domainError!!, color = StatusRed, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        placeholder = { Text(stringResource(R.string.filter_search_placeholder), style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(onClick = {
                        exportedText = viewModel.exportRules()
                        showExportDialog = true
                    }) {
                        Text(stringResource(R.string.action_export), color = OrangePrimary, fontWeight = FontWeight.SemiBold)
                    }

                    TextButton(onClick = { showImportDialog = true }) {
                        Text(stringResource(R.string.action_import), color = OrangePrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (currentList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                stringResource(R.string.filter_search_no_match, searchQuery)
                            } else {
                                if (isAllowTab) stringResource(R.string.filter_empty_allow)
                                else stringResource(R.string.filter_empty_block)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(currentList, key = { it.id }) { rule ->
                    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
                    val formattedDate = remember(rule.createdAt) { dateFormat.format(Date(rule.createdAt)) }
                    val statusText = if (isAllowTab) stringResource(R.string.filter_status_allowed) else stringResource(R.string.filter_status_blocked)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = rule.domain,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$statusText • $formattedDate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = { viewModel.deleteCustomRule(rule.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
