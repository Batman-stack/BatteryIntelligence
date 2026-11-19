package com.batteryintelligence.ui.charging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryintelligence.domain.model.ChargingConfidence
import com.batteryintelligence.ui.components.BatterySideDisclosureBanner
import com.batteryintelligence.ui.components.ChargingSpeedBadge
import com.batteryintelligence.ui.components.EtaConfidenceBadge
import com.batteryintelligence.ui.components.MetricTile
import com.batteryintelligence.ui.components.StatusPill
import com.batteryintelligence.ui.components.TactileCard
import com.batteryintelligence.ui.components.TelemetryLineChart
import com.batteryintelligence.ui.components.UnitFormatters
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SessionCurveMode(val label: String, val unit: String) {
    POWER("Power (W)", " W"),
    CURRENT("Current (mA)", " mA"),
    TEMP("Temp (°C)", "°C")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChargingMonitorScreen(
    onSessionClick: (Long) -> Unit = {},
    onOpenDisclosure: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChargingMonitorViewModel = hiltViewModel()
) {
    val liveIntelligence by viewModel.liveIntelligence.collectAsStateWithLifecycle()
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val activeSamples by viewModel.activeSessionSamples.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val benchmarks by viewModel.benchmarks.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    var curveMode by remember { mutableStateOf(SessionCurveMode.POWER) }

    val semantic = LocalBatterySemanticColors.current
    val snapshot = liveIntelligence.snapshot
    val isPlugged = snapshot?.chargingSource?.isPlugged == true
    val eta = liveIntelligence.etaEstimate

    val decimatedCurve = remember(activeSamples) {
        if (activeSamples.size <= 100) activeSamples
        else {
            val step = (activeSamples.size / 100).coerceAtLeast(1)
            activeSamples.filterIndexed { idx, _ -> idx % step == 0 || idx == activeSamples.lastIndex }
        }
    }

    val curvePoints = remember(decimatedCurve, curveMode) {
        decimatedCurve.mapNotNull { pt ->
            val yVal: Double? = when (curveMode) {
                SessionCurveMode.POWER -> pt.smoothedPowerWatts
                SessionCurveMode.CURRENT -> pt.currentNowMa
                SessionCurveMode.TEMP -> pt.temperatureCelsius?.toDouble()
            }
            if (yVal != null && yVal.isFinite()) pt.elapsedMs to yVal else null
        }
    }

    val elapsedFormatter = remember(curvePoints) {
        CartesianValueFormatter { _, value, _ ->
            val idx = value.toInt().coerceIn(0, (curvePoints.size - 1).coerceAtLeast(0))
            val mins = ((curvePoints.getOrNull(idx)?.first ?: 0L) / 60_000L)
            "${mins}m"
        }
    }

    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    val dateTimeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TactileCard(
                containerColor = if (isPlugged) semantic.mintSurface else MaterialTheme.colorScheme.surface,
                borderColor = if (isPlugged) semantic.mintBorder else semantic.cardBorder,
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isPlugged) {
                            "Charging • ${snapshot?.chargingSource?.displayLabel ?: "Plugged"}"
                        } else {
                            "Charger Disconnected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isPlugged) semantic.emeraldText else MaterialTheme.colorScheme.onSurface
                    )
                    ChargingSpeedBadge(tier = liveIntelligence.speedTier)
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isPlugged && snapshot != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = liveIntelligence.smoothedPowerWatts?.let {
                                    String.format(Locale.US, "%.1f W", it)
                                } ?: "Not Available",
                                style = MaterialTheme.typography.displayMedium,
                                color = semantic.emeraldText
                            )
                            Text(
                                text = "Heuristic speed tier • Phase: ${liveIntelligence.chargingPhase.displayLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (eta != null) {
                            EtaConfidenceBadge(confidence = eta.confidence)
                        }
                    }

                    if (liveIntelligence.isStallOrTrickleDetected || eta?.isThermalThrottlingAdjusted == true) {
                        Spacer(modifier = Modifier.height(8.dp))
                        StatusPill(
                            text = if (liveIntelligence.isStallOrTrickleDetected) {
                                "Trickle / Stall Detected — Check Cable or Load"
                            } else {
                                "ETA Adjusted for Elevated Temperature"
                            },
                            textColor = semantic.warmText,
                            containerColor = semantic.warmContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val targetPercent = eta?.targetPercent ?: (prefs.targetChargeAlertPercent ?: 80)
                    val targetEtaLabel = when {
                        eta == null || eta.confidence == ChargingConfidence.CALCULATING -> "Calculating ETA..."
                        eta.isSlowTrickleClamped -> "Very slow / trickle"
                        eta.timeToTargetMs == 0L -> "Target $targetPercent% reached"
                        eta.timeToTargetMs != null -> formatEtaDuration(eta.timeToTargetMs)
                        else -> "Not Available"
                    }
                    val fullEtaLabel = when {
                        eta == null || eta.confidence == ChargingConfidence.CALCULATING -> "Calculating..."
                        eta.isSlowTrickleClamped -> "Very slow"
                        eta.timeTo100Ms == 0L -> "Full"
                        eta.timeTo100Ms != null -> {
                            val clock = eta.clockTimeAt100Ms?.let { " (${timeFormat.format(Date(it))})" }.orEmpty()
                            "${formatEtaDuration(eta.timeTo100Ms)}$clock"
                        }
                        else -> "Not Available"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = "To $targetPercent% Target",
                            value = targetEtaLabel,
                            subtitle = eta?.clockTimeAtTargetMs?.let { "Est. ${timeFormat.format(Date(it))}" },
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.background
                        )
                        MetricTile(
                            label = "To 100% (CC/CV)",
                            value = fullEtaLabel,
                            subtitle = eta?.timeTo80Ms?.takeIf { it > 0L }?.let { "80% in ${formatEtaDuration(it)}" },
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = "Smoothed Current",
                            value = liveIntelligence.smoothedCurrentMa?.let {
                                String.format(Locale.US, "%.0f mA", abs(it))
                            } ?: "Not Available",
                            subtitle = activeSession?.let { "${it.startLevelPercent}% → ${snapshot.levelPercent ?: it.startLevelPercent}%" },
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.background
                        )
                        MetricTile(
                            label = "Thermal Slope",
                            value = liveIntelligence.thermalSlopeCPerMin?.let {
                                String.format(Locale.US, "%+.2f °C/m", it)
                            } ?: "-- °C/m",
                            subtitle = "Now ${UnitFormatters.formatTemperature(snapshot.temperatureCelsius, prefs.temperatureUnit)}",
                            modifier = Modifier.weight(1f),
                            containerColor = MaterialTheme.colorScheme.background,
                            valueColor = when {
                                liveIntelligence.isThermalSlopeAlert -> semantic.alertText
                                liveIntelligence.isThermalSlopeElevated -> semantic.warmText
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }

                    // Rolling ETA Prediction History
                    val history = eta?.predictionHistory.orEmpty()
                    if (history.size >= 2) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Recent ETA Updates",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            history.takeLast(4).forEach { pt ->
                                val label = "${pt.levelPercent}%: ${pt.estimatedTimeTo100Ms?.let(::formatEtaDuration) ?: "--"}"
                                StatusPill(
                                    text = label,
                                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    containerColor = MaterialTheme.colorScheme.background
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Plug in a charger to track real-time smoothed wattage, CC/CV phase transitions, and hybrid ETA.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val currentTarget = (prefs.targetChargeAlertPercent ?: 80).coerceIn(50, 100)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Target Charge Alarm",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$currentTarget%",
                        style = MaterialTheme.typography.titleSmall,
                        color = semantic.emeraldText
                    )
                }
                Slider(
                    value = currentTarget.toFloat(),
                    onValueChange = { viewModel.updateTargetChargeAlert(it.roundToInt()) },
                    valueRange = 50f..100f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = semantic.emeraldStroke,
                        activeTrackColor = semantic.emeraldStroke
                    )
                )
            }
        }

        item {
            BatterySideDisclosureBanner(onOpenDetails = onOpenDisclosure)
        }

        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(
                    text = "Active Session Curve",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SessionCurveMode.entries.forEach { mode ->
                        FilterChip(
                            selected = curveMode == mode,
                            onClick = { curveMode = mode },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = semantic.mintSurface,
                                selectedLabelColor = semantic.emeraldText
                            )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                val lineColor = when (curveMode) {
                    SessionCurveMode.POWER -> semantic.emeraldStroke
                    SessionCurveMode.CURRENT -> semantic.fastTealText
                    SessionCurveMode.TEMP -> semantic.warmStroke
                }
                TelemetryLineChart(
                    xValues = curvePoints.indices.toList(),
                    yValues = curvePoints.map { it.second },
                    lineColor = lineColor,
                    unitSuffix = curveMode.unit,
                    emptyMessage = "Start or continue a charging session to plot CC/CV curves.",
                    xFormatter = elapsedFormatter
                )
            }
        }

        if (benchmarks.isNotEmpty()) {
            item {
                Text(
                    text = "Observed Charger Benchmarks",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(benchmarks) { benchmark ->
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${benchmark.chargingSource.displayLabel} • ${benchmark.speedTier.displayLabel}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        StatusPill(
                            text = "${benchmark.sessionCount} sessions",
                            textColor = semantic.emeraldText,
                            containerColor = semantic.mintSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = buildString {
                            benchmark.avgPowerWatts?.let { append(String.format(Locale.US, "Avg %.1f W", it)) }
                            benchmark.maxPowerWatts?.let { append(String.format(Locale.US, " • Peak %.1f W", it)) }
                            benchmark.avgEfficiencyPercent?.let { append(String.format(Locale.US, " • Eff %.0f%%", it)) }
                            benchmark.avgTempRiseCelsius?.let { append(String.format(Locale.US, " • +%.1f°C", it)) }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                text = "Recent Charging Sessions",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (recentSessions.isEmpty()) {
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = "No recorded charging sessions yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(recentSessions, key = { it.id }) { session ->
                TactileCard(
                    containerColor = MaterialTheme.colorScheme.surface,
                    onClick = { onSessionClick(session.id) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${session.startLevelPercent}% → ${session.endLevelPercent ?: "?"}%",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            session.dominantSpeedTier?.let { ChargingSpeedBadge(tier = it) }
                            StatusPill(
                                text = session.chargingSource.displayLabel,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${dateTimeFormat.format(Date(session.startTimeMs))} • ${formatEtaDuration(session.durationMs)} • ${session.terminationReason.displayLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val metricsLine = listOfNotNull(
                        session.avgPowerWatts?.let { String.format(Locale.US, "Avg %.1f W", it) },
                        session.peakPowerWatts?.let { String.format(Locale.US, "Peak %.1f W", it) },
                        session.energyAddedWh?.let { String.format(Locale.US, "%.1f Wh", it) },
                        session.chargingEfficiencyPercent?.let { String.format(Locale.US, "Eff %.0f%%", it) }
                    ).joinToString(" • ")
                    if (metricsLine.isNotEmpty()) {
                        Text(
                            text = metricsLine,
                            style = MaterialTheme.typography.labelMedium,
                            color = semantic.emeraldText
                        )
                    }
                }
            }
        }
    }
}

private fun formatEtaDuration(ms: Long): String {
    val totalMins = (ms / 60_000L).coerceAtLeast(1L)
    val h = totalMins / 60L
    val m = totalMins % 60L
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
