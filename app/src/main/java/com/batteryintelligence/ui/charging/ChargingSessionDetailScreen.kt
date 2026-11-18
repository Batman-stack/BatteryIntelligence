package com.batteryintelligence.ui.charging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChargingSessionDetailScreen(
    onOpenDisclosure: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChargingSessionDetailViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val samples by viewModel.samples.collectAsStateWithLifecycle()
    val semantic = LocalBatterySemanticColors.current
    val dateFmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    val powerPoints = remember(samples) {
        samples.mapNotNull { s -> s.smoothedPowerWatts?.let { s.elapsedMs to it } }
    }
    val currentPoints = remember(samples) {
        samples.mapNotNull { s -> s.currentNowMa?.let { s.elapsedMs to it } }
    }
    val tempPoints = remember(samples) {
        samples.mapNotNull { s -> s.temperatureCelsius?.toDouble()?.let { s.elapsedMs to it } }
    }

    val powerTimeFmt = remember(powerPoints) {
        CartesianValueFormatter { _, v, _ ->
            val idx = v.toInt().coerceIn(0, (powerPoints.size - 1).coerceAtLeast(0))
            "${(powerPoints.getOrNull(idx)?.first ?: 0L) / 60_000L}m"
        }
    }
    val currentTimeFmt = remember(currentPoints) {
        CartesianValueFormatter { _, v, _ ->
            val idx = v.toInt().coerceIn(0, (currentPoints.size - 1).coerceAtLeast(0))
            "${(currentPoints.getOrNull(idx)?.first ?: 0L) / 60_000L}m"
        }
    }
    val tempTimeFmt = remember(tempPoints) {
        CartesianValueFormatter { _, v, _ ->
            val idx = v.toInt().coerceIn(0, (tempPoints.size - 1).coerceAtLeast(0))
            "${(tempPoints.getOrNull(idx)?.first ?: 0L) / 60_000L}m"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        val currentSession = session
        if (currentSession == null) {
            item {
                TactileCard {
                    Text(
                        text = "Loading charging session...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            return@LazyColumn
        }

        item {
            TactileCard(
                containerColor = semantic.mintSurface,
                borderColor = semantic.mintBorder
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentSession.startLevelPercent}% → ${currentSession.endLevelPercent ?: "?"}%",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        currentSession.dominantSpeedTier?.let { ChargingSpeedBadge(tier = it) }
                        StatusPill(
                            text = currentSession.terminationReason.displayLabel,
                            textColor = semantic.emeraldText,
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${currentSession.chargingSource.displayLabel} • Started ${dateFmt.format(Date(currentSession.startTimeMs))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Avg / Peak Power",
                    value = currentSession.avgPowerWatts?.let { String.format(Locale.US, "%.1f W", it) } ?: "-- W",
                    subtitle = currentSession.peakPowerWatts?.let { String.format(Locale.US, "Peak %.1f W", it) },
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Avg / Peak Current",
                    value = currentSession.avgCurrentMa?.let { String.format(Locale.US, "%.0f mA", it) } ?: "-- mA",
                    subtitle = currentSession.peakCurrentMa?.let { String.format(Locale.US, "Peak %.0f mA", it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Energy & Charge",
                    value = currentSession.energyAddedWh?.let { String.format(Locale.US, "%.1f Wh", it) } ?: "-- Wh",
                    subtitle = currentSession.energyAddedMah?.let { String.format(Locale.US, "+%.0f mAh", it) },
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Temp Range",
                    value = currentSession.averageTemperatureCelsius?.let { String.format(Locale.US, "%.1f °C", it) } ?: "-- °C",
                    subtitle = if (currentSession.minTemperatureCelsius != null && currentSession.peakTemperatureCelsius != null) {
                        String.format(Locale.US, "%.1f–%.1f °C", currentSession.minTemperatureCelsius, currentSession.peakTemperatureCelsius)
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "CC → CV Knee",
                    value = currentSession.ccCvTransitionPercent?.let { "$it%" } ?: "Not Reached",
                    subtitle = currentSession.chargingEfficiencyPercent?.let {
                        String.format(Locale.US, "Coulombic %.1f%%", it)
                    },
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Duration",
                    value = formatDuration(currentSession.durationMs),
                    subtitle = currentSession.etaError100Ms?.let { errMs ->
                        val sign = if (errMs >= 0) "+" else "-"
                        "ETA err $sign${formatDuration(kotlin.math.abs(errMs))}"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            BatterySideDisclosureBanner(onOpenDetails = onOpenDisclosure)
        }

        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(text = "Power Curve (W)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TelemetryLineChart(
                    xValues = powerPoints.indices.toList(),
                    yValues = powerPoints.map { it.second },
                    lineColor = semantic.emeraldStroke,
                    unitSuffix = " W",
                    emptyMessage = "Insufficient high-resolution power points for this session.",
                    xFormatter = powerTimeFmt
                )
            }
        }

        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(text = "Current Curve (mA)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TelemetryLineChart(
                    xValues = currentPoints.indices.toList(),
                    yValues = currentPoints.map { it.second },
                    lineColor = semantic.fastTealText,
                    unitSuffix = " mA",
                    emptyMessage = "Insufficient high-resolution current points for this session.",
                    xFormatter = currentTimeFmt
                )
            }
        }

        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(text = "Thermal Curve (°C)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                TelemetryLineChart(
                    xValues = tempPoints.indices.toList(),
                    yValues = tempPoints.map { it.second },
                    lineColor = semantic.warmStroke,
                    unitSuffix = "°C",
                    emptyMessage = "Insufficient thermal points for this session.",
                    xFormatter = tempTimeFmt
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val mins = (ms / 60_000L).coerceAtLeast(1L)
    val h = mins / 60L
    val m = mins % 60L
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
