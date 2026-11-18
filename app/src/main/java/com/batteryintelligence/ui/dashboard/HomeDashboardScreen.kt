package com.batteryintelligence.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryintelligence.ui.components.BatterySideDisclosureBanner
import com.batteryintelligence.ui.components.ChargingSpeedBadge
import com.batteryintelligence.ui.components.MetricTile
import com.batteryintelligence.ui.components.StatusPill
import com.batteryintelligence.ui.components.TactileCard
import com.batteryintelligence.ui.components.TelemetryLineChart
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors
import com.batteryintelligence.ui.theme.LocalReducedMotion
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class HomeChartMetric(val label: String, val unit: String) {
    POWER("Power (W)", " W"),
    CURRENT("Current (mA)", " mA"),
    TEMP("Temp (°C)", "°C")
}

@Composable
fun HomeDashboardScreen(
    onOpenDisclosure: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeDashboardViewModel = hiltViewModel()
) {
    val liveState by viewModel.liveState.collectAsStateWithLifecycle()
    val samples by viewModel.recentSamples.collectAsStateWithLifecycle()
    val selectedWindow by viewModel.selectedWindow.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var selectedMetric by remember { mutableStateOf(HomeChartMetric.POWER) }

    val snapshot = liveState.snapshot
    val semantic = LocalBatterySemanticColors.current
    val reducedMotion = LocalReducedMotion.current
    val isCharging = snapshot?.isCharging == true

    val displayPower = liveState.smoothedPowerWatts ?: snapshot?.powerWatts
    val animatedPower by animateFloatAsState(
        targetValue = (displayPower ?: 0.0).toFloat(),
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "homePower"
    )
    val displayCurrentMa = liveState.smoothedCurrentMa ?: snapshot?.effectiveCurrentMa
    val animatedCurrent by animateFloatAsState(
        targetValue = displayCurrentMa?.let { abs(it).toFloat() } ?: 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "homeCurrent"
    )

    val decimatedSamples = remember(samples) {
        if (samples.size <= 100) samples
        else {
            val step = (samples.size / 100).coerceAtLeast(1)
            samples.filterIndexed { idx, _ -> idx % step == 0 || idx == samples.lastIndex }
        }
    }

    val chartPoints = remember(decimatedSamples, selectedMetric) {
        decimatedSamples.mapNotNull { rec ->
            val yVal: Double? = when (selectedMetric) {
                HomeChartMetric.POWER -> rec.powerWatts
                HomeChartMetric.CURRENT -> rec.currentNowMa?.let { abs(it) }
                HomeChartMetric.TEMP -> rec.temperatureCelsius?.toDouble()
            }
            if (yVal != null && yVal.isFinite()) rec.timestampMs to yVal else null
        }
    }

    val timeFormatter = remember(chartPoints) {
        val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
        CartesianValueFormatter { _, value, _ ->
            val idx = value.toInt().coerceIn(0, (chartPoints.size - 1).coerceAtLeast(0))
            chartPoints.getOrNull(idx)?.first?.let { fmt.format(Date(it)) } ?: ""
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TactileCard(
                containerColor = if (isCharging) semantic.mintSurface else MaterialTheme.colorScheme.surface,
                borderColor = if (isCharging) semantic.mintBorder else semantic.cardBorder,
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isCharging) "Charging via ${snapshot?.chargingSource?.displayLabel ?: "Plugged"}" else "On Battery",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCharging) semantic.emeraldText else MaterialTheme.colorScheme.onSurface
                    )
                    ChargingSpeedBadge(tier = liveState.speedTier)
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    AnimatedContent(
                        targetState = snapshot?.levelPercent,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(180))
                            }
                        },
                        label = "socAnim"
                    ) { level ->
                        Text(
                            text = level?.let { "$it%" } ?: "--%",
                            style = MaterialTheme.typography.displayLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill(
                            text = if (prefs.foregroundServiceEnabled) "FGS Active" else "In-App Live",
                            textColor = if (prefs.foregroundServiceEnabled) semantic.emeraldText else MaterialTheme.colorScheme.onSurfaceVariant,
                            containerColor = if (prefs.foregroundServiceEnabled) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceVariant
                        )
                        liveState.etaEstimate?.timeToTargetMs?.takeIf { isCharging && it > 0L }?.let { etaMs ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${formatEtaShort(etaMs)} to ${liveState.etaEstimate?.targetPercent ?: 80}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = semantic.emeraldText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { ((snapshot?.levelPercent ?: 0) / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = semantic.emeraldStroke,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    label = "Battery-Side Power",
                    value = if (displayPower != null) String.format(Locale.US, "%.1f W", animatedPower) else "-- W",
                    subtitle = if (snapshot?.isNetBatteryDischargingWhilePlugged == true) "Net drain on charger" else liveState.chargingPhase.displayLabel,
                    modifier = Modifier.weight(1f),
                    valueColor = if (isCharging) semantic.emeraldText else MaterialTheme.colorScheme.onSurface
                )
                MetricTile(
                    label = "Current",
                    value = if (displayCurrentMa != null) String.format(Locale.US, "%.0f mA", animatedCurrent) else "-- mA",
                    subtitle = snapshot?.telemetrySources?.currentSource?.displayLabel,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            val tempC = snapshot?.temperatureCelsius
            val slope = liveState.thermalSlopeCPerMin
            val tempValueColor = when {
                liveState.isThermalSlopeAlert || (tempC != null && tempC >= (prefs.highTempAlertCelsius ?: 42f)) -> semantic.alertText
                liveState.isThermalSlopeElevated || (tempC != null && tempC >= 38f) -> semantic.warmText
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricTile(
                    label = "Terminal Voltage",
                    value = snapshot?.voltageVolts?.let { String.format(Locale.US, "%.2f V", it) } ?: "-- V",
                    subtitle = snapshot?.voltageMv?.let { "$it mV" },
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Temperature",
                    value = tempC?.let { String.format(Locale.US, "%.1f °C", it) } ?: "-- °C",
                    subtitle = slope?.let { String.format(Locale.US, "Slope %+.2f °C/min", it) } ?: "Stabilizing slope",
                    modifier = Modifier.weight(1f),
                    valueColor = tempValueColor
                )
            }
        }

        item {
            BatterySideDisclosureBanner(onOpenDetails = onOpenDisclosure)
        }

        item {
            TactileCard(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Telemetry Curve",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${samples.size} pts",
                        style = MaterialTheme.typography.labelMedium,
                        color = semantic.mutedCaption
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardTimeWindow.entries.forEach { window ->
                        FilterChip(
                            selected = selectedWindow == window,
                            onClick = { viewModel.selectTimeWindow(window) },
                            label = { Text(window.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = semantic.mintSurface,
                                selectedLabelColor = semantic.emeraldText
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeChartMetric.entries.forEach { metric ->
                        FilterChip(
                            selected = selectedMetric == metric,
                            onClick = { selectedMetric = metric },
                            label = { Text(metric.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = semantic.mintSurface,
                                selectedLabelColor = semantic.emeraldText
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val lineColor = when (selectedMetric) {
                    HomeChartMetric.POWER -> semantic.emeraldStroke
                    HomeChartMetric.CURRENT -> semantic.fastTealText
                    HomeChartMetric.TEMP -> semantic.warmStroke
                }

                TelemetryLineChart(
                    xValues = chartPoints.indices.toList(),
                    yValues = chartPoints.map { it.second },
                    lineColor = lineColor,
                    unitSuffix = selectedMetric.unit,
                    emptyMessage = "Collecting samples for ${selectedWindow.label} trend...",
                    xFormatter = timeFormatter
                )
            }
        }
    }
}

private fun formatEtaShort(ms: Long): String {
    val mins = (ms / 60_000L).coerceAtLeast(1L)
    val h = mins / 60L
    val m = mins % 60L
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
