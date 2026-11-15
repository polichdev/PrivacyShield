package com.example.privacyshield.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.privacyshield.R
import com.example.privacyshield.ui.theme.OrangePrimary
import com.example.privacyshield.ui.theme.StatusAmber
import com.example.privacyshield.ui.theme.StatusGreen
import com.example.privacyshield.ui.theme.StatusRed
import com.example.privacyshield.ui.viewmodels.DashboardViewModel
import com.example.privacyshield.vpn.ProtectionController
import com.example.privacyshield.vpn.ProtectionState
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val protectionState by viewModel.protectionState.collectAsStateWithLifecycle()
    val stats by viewModel.statsState.collectAsStateWithLifecycle()
    val activeRulesCount by viewModel.activeRulesCount.collectAsStateWithLifecycle()
    val uptimeMillis by viewModel.uptimeMillis.collectAsStateWithLifecycle()

    val vpnPrepareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ProtectionController.start(context)
        } else {
            ProtectionController.updateState(ProtectionState.Disconnected)
        }
    }

    val onToggleProtection = {
        viewModel.onToggleProtection(context, vpnPrepareLauncher)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {

        item {
            MetricsGrid(
                totalQueries = stats.totalQueries,
                blockedQueries = stats.blockedQueries,
                allowedQueries = stats.allowedQueries,
                activeRules = activeRulesCount
            )
        }

        item {
            TurboConnectionHero(
                state = protectionState,
                uptimeMillis = uptimeMillis,
                onToggle = onToggleProtection
            )
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TurboConnectionHero(
    state: ProtectionState,
    uptimeMillis: Long,
    onToggle: () -> Unit
) {
    val isConnected = state is ProtectionState.Connected
    val isConnecting = state is ProtectionState.Connecting || state is ProtectionState.Disconnecting

    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseAlpha"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
            Text(text = when (state) {
                is ProtectionState.Connected -> stringResource(R.string.hero_on)
                is ProtectionState.Connecting -> stringResource(R.string.hero_connecting)
                is ProtectionState.Disconnecting -> stringResource(R.string.hero_disconnecting)
                is ProtectionState.Error -> state.message
                else -> stringResource(R.string.hero_off)
            }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = if (isConnected) OrangePrimary else MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center, maxLines = 2)
        }
        Box(Modifier.fillMaxWidth().height(32.dp), contentAlignment = Alignment.Center) {
            if (isConnected) Text(formatUptime(uptimeMillis), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(modifier = Modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * 0.42f
                drawCircle(brush = Brush.radialGradient(listOf(OrangePrimary.copy(alpha = if (isConnected) 0.20f else 0.07f), Color.Transparent)), radius = radius)
                for (fraction in listOf(0.62f, 0.82f, 1f)) drawCircle(OrangePrimary.copy(alpha = if (isConnected) 0.18f else 0.07f), radius = radius * fraction, style = Stroke(width = 1.dp.toPx()))
                if (isConnected || isConnecting) drawCircle(OrangePrimary.copy(alpha = pulseAlpha * 0.4f), radius = radius * pulseScale / 1.38f, style = Stroke(width = 2.dp.toPx()))
            }
            Surface(onClick = onToggle, shape = CircleShape, color = if (isConnected) OrangePrimary else MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(2.dp, OrangePrimary.copy(alpha = 0.7f)), shadowElevation = 12.dp, modifier = Modifier.size(116.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    if (isConnecting) CircularProgressIndicator(color = OrangePrimary, modifier = Modifier.size(44.dp))
                    else Icon(painterResource(R.drawable.ic_nav_shield), contentDescription = if (isConnected) stringResource(R.string.hero_disable) else stringResource(R.string.hero_enable), tint = if (isConnected) Color.White else OrangePrimary, modifier = Modifier.size(50.dp))
                }
            }
        }
        Text(if (isConnected) stringResource(R.string.hero_stop_hint) else stringResource(R.string.hero_start_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun MetricsGrid(
    totalQueries: Long,
    blockedQueries: Long,
    allowedQueries: Long,
    activeRules: Int
) {
    val blockPercent = if (totalQueries > 0) {
        ((blockedQueries.toDouble() / totalQueries) * 100).toInt()
    } else 0

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = stringResource(R.string.metric_total_queries),
                value = "$totalQueries",
                accentColor = OrangePrimary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = stringResource(R.string.metric_blocked),
                value = "$blockedQueries ($blockPercent%)",
                accentColor = StatusRed,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = stringResource(R.string.metric_allowed),
                value = "$allowedQueries",
                accentColor = StatusGreen,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = stringResource(R.string.metric_active_rules),
                value = "$activeRules",
                accentColor = StatusAmber,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
        }
    }
}

private fun formatUptime(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
}

