package com.batteryintelligence.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryintelligence.domain.model.DiagnosticSeverity
import com.batteryintelligence.domain.model.MetricSource
import com.batteryintelligence.domain.model.ThermalSeverityLevel
import com.batteryintelligence.ui.components.EtaConfidenceBadge
import com.batteryintelligence.ui.components.MetricSourceTag
import com.batteryintelligence.ui.components.MetricTile
import com.batteryintelligence.ui.components.StatusPill
import com.batteryintelligence.ui.components.TactileCard
import com.batteryintelligence.ui.components.TactilePillButton
import com.batteryintelligence.ui.components.TelemetryLineChart
import com.batteryintelligence.ui.components.UnitFormatters
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors
import com.patrykandpatrick.vico.core.cartesian.data.CartesianValueFormatter
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BatteryHealthScreen(
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: BatteryHealthViewModel = hiltViewModel()
) {
    val report by viewModel.healthReport.collectAsStateWithLifecycle()
    val trendSessions by viewModel.capacityTrendSessions.collectAsStateWithLifecycle()
    val overnightLogs by viewModel.recentOvernightLogs.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val deviceInfo by viewModel.deviceIntelligence.collectAsStateWithLifecycle()
    val semantic = LocalBatterySemanticColors.current

    var showCpuCores by remember { mutableStateOf(false) }
    var showSensorsList by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDeviceIntelligence()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val capacityPoints = remember(trendSessions) {
        trendSessions.mapNotNull { s ->
            s.estimatedCapacityMah?.let { s.startTimeMs to it }
        }
    }

    val dateFormatter = remember(capacityPoints) {
        val fmt = DateFormat.getDateInstance(DateFormat.SHORT)
        CartesianValueFormatter { _, v, _ ->
            val idx = v.toInt().coerceIn(0, (capacityPoints.size - 1).coerceAtLeast(0))
            capacityPoints.getOrNull(idx)?.first?.let { fmt.format(Date(it)) } ?: ""
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. State of Health Hero Card
        item {
            TactileCard(
                containerColor = semantic.mintSurface,
                borderColor = semantic.mintBorder,
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "State of Health",
                        style = MaterialTheme.typography.titleMedium,
                        color = semantic.emeraldText
                    )
                    report?.stateOfHealthSource?.let { MetricSourceTag(source = it) }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = report?.stateOfHealthPercent?.let { "$it%" } ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (report?.stateOfHealthSource) {
                        MetricSource.BATTERY_MANAGER, MetricSource.SYSFS ->
                            "Reported directly by device battery fuel gauge."
                        MetricSource.SESSION_DERIVED ->
                            "Derived from ${report?.evaluatedSessionsCount ?: 0} charging sessions (≥20% charge delta)."
                        else ->
                            "Complete ≥20% charging sessions or set Design Capacity in Settings to calculate SoH."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Design vs Estimated Capacity Tiles
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "Design Capacity",
                    value = UnitFormatters.formatCapacity(report?.designCapacityMah, prefs.capacityUnit),
                    subtitle = report?.designCapacitySource?.displayLabel,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Estimated Full",
                    value = UnitFormatters.formatCapacity(report?.estimatedFullCapacityMah, prefs.capacityUnit),
                    subtitle = "${report?.evaluatedSessionsCount ?: 0} sessions evaluated",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // 3. Cycle Count & Thermal History Intelligence Card
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Cycle Count & Thermal Wear", style = MaterialTheme.typography.titleMedium)
                    report?.cycleCountSource?.let { MetricSourceTag(source = it) }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = "Hardware Cycles",
                        value = report?.cycleCount?.toString() ?: "N/A",
                        subtitle = if (report?.cycleCount != null) "Android HAL" else "Not exposed",
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Tracked Cycles",
                        value = report?.estimatedCycleCount?.let { String.format(Locale.US, "%.1f", it) } ?: "--",
                        subtitle = "Cumulative logged",
                        modifier = Modifier.weight(1f)
                    )
                }

                report?.cycleCountConfidence?.let { conf ->
                    Spacer(modifier = Modifier.height(8.dp))
                    EtaConfidenceBadge(confidence = conf)
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricTile(
                        label = "Avg Charge Temp",
                        value = UnitFormatters.formatTemperature(report?.averageChargingTempCelsius, prefs.temperatureUnit),
                        subtitle = "Peak ${UnitFormatters.formatTemperature(report?.maxObservedTempCelsius, prefs.temperatureUnit)}",
                        modifier = Modifier.weight(1f)
                    )
                    MetricTile(
                        label = "Hot Sessions (≥40°C)",
                        value = (report?.highTempSessionCount ?: 0).toString(),
                        subtitle = if ((report?.highTempSessionCount ?: 0) > 0) "Accelerates wear" else "Cool history",
                        modifier = Modifier.weight(1f),
                        valueColor = if ((report?.highTempSessionCount ?: 0) > 0) semantic.warmText else semantic.emeraldText
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Hardware cycle count requires Android 14+ OEM HAL support. Tracked cycles accumulate from locally logged charging sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onNavigateToSettings) {
                    Text("Adjust Design Capacity Override", color = semantic.emeraldText)
                }
            }
        }

        // 4. Estimated Capacity Degradation Trend Chart
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(
                    text = "Estimated Capacity Trend (mAh)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                TelemetryLineChart(
                    xValues = capacityPoints.indices.toList(),
                    yValues = capacityPoints.map { it.second },
                    lineColor = semantic.emeraldStroke,
                    unitSuffix = " mAh",
                    emptyMessage = "Record at least 2 qualifying charging sessions (≥20% delta) to plot capacity trend.",
                    xFormatter = dateFormatter
                )
            }
        }

        // 5. Recent Overnight Drain Reports
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(
                    text = "Overnight Standby Reports",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (overnightLogs.isEmpty()) {
                    Text(
                        text = "No overnight standby windows captured yet. Keep the device unplugged overnight to measure standby drain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        overnightLogs.forEachIndexed { idx, log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = log.nightDateIso,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = if (log.wasPluggedIn) {
                                            "Plugged in • ${log.startLevelPercent ?: "--"}% → ${log.endLevelPercent ?: "--"}%"
                                        } else {
                                            "Drop ${log.batteryDropPercent ?: 0}% over ${UnitFormatters.formatDurationCompact(log.durationMs)}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusPill(
                                    text = if (log.wasPluggedIn) {
                                        "On Charger"
                                    } else {
                                        log.drainRatePercentPerHour?.let {
                                            String.format(Locale.US, "%.2f %%/hr", it)
                                        } ?: "--"
                                    },
                                    textColor = when {
                                        log.wasPluggedIn -> semantic.emeraldText
                                        (log.drainRatePercentPerHour ?: 0f) > 1.5f -> semantic.warmText
                                        else -> semantic.emeraldText
                                    },
                                    containerColor = when {
                                        log.wasPluggedIn -> semantic.mintSurface
                                        (log.drainRatePercentPerHour ?: 0f) > 1.5f -> semantic.warmContainer
                                        else -> semantic.mintSurface
                                    }
                                )
                            }
                            if (idx < overnightLogs.lastIndex) {
                                HorizontalDivider(color = semantic.cardBorder)
                            }
                        }
                    }
                }
            }
        }

        // 6. Actionable Drain Diagnostics & Battery Saver Assistant
        deviceInfo?.let { dev ->
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = "Drain Diagnostics & Battery Assistant",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        dev.insights.forEach { insight ->
                            val (pillBg, pillFg) = when (insight.severity) {
                                DiagnosticSeverity.GOOD -> semantic.mintSurface to semantic.emeraldText
                                DiagnosticSeverity.INFO -> semantic.fastTealContainer to semantic.fastTealText
                                DiagnosticSeverity.WARNING -> semantic.warmContainer to semantic.warmText
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = insight.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    StatusPill(
                                        text = insight.severity.displayLabel,
                                        textColor = pillFg,
                                        containerColor = pillBg
                                    )
                                }
                                Text(
                                    text = insight.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            HorizontalDivider(color = semantic.cardBorder)
                        }
                    }
                }
            }

            // 7. Device Intelligence — System, Deep Sleep & Display
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Device Intelligence",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TactilePillButton(
                            text = "Refresh",
                            onClick = { viewModel.refreshDeviceIntelligence() }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "${dev.systemProfile.manufacturer} ${dev.systemProfile.model} • Android ${dev.systemProfile.androidVersion} (API ${dev.systemProfile.sdkInt})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    dev.systemProfile.securityPatch?.let { patch ->
                        Text(
                            text = "Security Patch: $patch • Board: ${dev.cpu.hardwareBoard}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = "Deep Sleep",
                            value = String.format(Locale.US, "%.1f%%", dev.systemProfile.deepSleepPercent),
                            subtitle = UnitFormatters.formatDurationCompact(dev.systemProfile.deepSleepMs),
                            modifier = Modifier.weight(1f),
                            valueColor = if (dev.systemProfile.deepSleepPercent >= 40f) semantic.emeraldText else semantic.warmText
                        )
                        MetricTile(
                            label = "System Uptime",
                            value = UnitFormatters.formatDurationCompact(dev.systemProfile.elapsedRealtimeMs),
                            subtitle = "Awake ${UnitFormatters.formatDurationCompact(dev.systemProfile.awakeUptimeMs)}",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricTile(
                            label = "Display",
                            value = dev.displaySot.refreshRateHz?.let { String.format(Locale.US, "%.0f Hz", it) } ?: "--",
                            subtitle = when (dev.displaySot.isAdaptiveBrightness) {
                                true -> "Adaptive brightness"
                                false -> "Manual brightness"
                                null -> "Brightness N/A"
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MetricTile(
                            label = "24h Screen-On",
                            value = dev.displaySot.rolling24hScreenOnMs?.let { UnitFormatters.formatDurationCompact(it) } ?: "Usage Off",
                            subtitle = dev.displaySot.rolling24hScreenOffMs?.let { "Off ${UnitFormatters.formatDurationCompact(it)}" } ?: "Grant in Analytics",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 8. RAM & Internal Storage Card
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Text(text = "Memory & Storage", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RAM (${UnitFormatters.formatBytesGb(dev.memoryStorage.usedRamBytes)} / ${UnitFormatters.formatBytesGb(dev.memoryStorage.totalRamBytes)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f%%", dev.memoryStorage.ramUsagePercent),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (dev.memoryStorage.isLowMemory) semantic.alertText else semantic.emeraldText
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (dev.memoryStorage.ramUsagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = if (dev.memoryStorage.isLowMemory) semantic.alertStroke else semantic.emeraldStroke,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Storage (${UnitFormatters.formatBytesGb(dev.memoryStorage.internalUsedBytes)} / ${UnitFormatters.formatBytesGb(dev.memoryStorage.internalTotalBytes)})",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f%%", dev.memoryStorage.storageUsagePercent),
                            style = MaterialTheme.typography.titleSmall,
                            color = semantic.emeraldText
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (dev.memoryStorage.storageUsagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp),
                        color = semantic.emeraldStroke,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                }
            }

            // 9. CPU / SoC & Per-Core Frequencies
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "CPU & SoC Profile", style = MaterialTheme.typography.titleMedium)
                        StatusPill(
                            text = "${dev.cpu.coreCount} Cores",
                            textColor = semantic.emeraldText,
                            containerColor = semantic.mintSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val socLabel = listOfNotNull(dev.cpu.socManufacturer, dev.cpu.socModel)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { dev.cpu.hardwareBoard }
                    Text(
                        text = "SoC: $socLabel",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "ABIs: ${dev.cpu.supportedAbis.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    if (!dev.cpu.freqReadableByKernel) {
                        Text(
                            text = "Per-core live frequencies are restricted by Android SELinux on this device; core count and SoC identity are reported from public Build APIs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TextButton(onClick = { showCpuCores = !showCpuCores }) {
                            Text(
                                text = if (showCpuCores) "Hide Per-Core Frequencies" else "Inspect ${dev.cpu.cores.size} CPU Cores",
                                color = semantic.emeraldText
                            )
                        }
                        if (showCpuCores) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                dev.cpu.cores.forEach { core ->
                                    val freqText = core.currentFreqMhz?.let { cur ->
                                        core.maxFreqMhz?.let { max -> "$cur / $max MHz" } ?: "$cur MHz"
                                    } ?: "Offline / Restricted"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Core ${core.coreIndex} (${core.governor ?: "n/a"})",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = freqText,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = semantic.emeraldText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 10. Network, Thermal Headroom & Hardware Sensors
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Text(text = "Network, Thermal & Sensors", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val thermalColor = when (dev.thermalSensors.thermalSeverity) {
                            ThermalSeverityLevel.NONE, ThermalSeverityLevel.LIGHT -> semantic.emeraldText
                            ThermalSeverityLevel.MODERATE -> semantic.warmText
                            ThermalSeverityLevel.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> semantic.alertText
                        }
                        MetricTile(
                            label = "Thermal Status",
                            value = dev.thermalSensors.thermalSeverity.displayLabel,
                            subtitle = dev.thermalSensors.thermalHeadroomForecast?.let {
                                String.format(Locale.US, "Headroom %.2f", it)
                            } ?: "Headroom N/A",
                            modifier = Modifier.weight(1f),
                            valueColor = thermalColor
                        )
                        MetricTile(
                            label = "Network",
                            value = dev.network.transportLabel,
                            subtitle = when (dev.network.isMetered) {
                                true -> "Metered"
                                false -> "Unmetered"
                                null -> "Disconnected"
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val categorySummary = dev.thermalSensors.sensorCategoryCounts.entries
                        .joinToString(" • ") { "${it.key}: ${it.value}" }
                    Text(
                        text = "Hardware Sensors: ${dev.thermalSensors.totalHardwareSensors} total" +
                            if (categorySummary.isNotBlank()) " ($categorySummary)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (dev.thermalSensors.topSensorNames.isNotEmpty()) {
                        TextButton(onClick = { showSensorsList = !showSensorsList }) {
                            Text(
                                text = if (showSensorsList) "Hide Sensor Inventory" else "View Sensor Names",
                                color = semantic.emeraldText
                            )
                        }
                        if (showSensorsList) {
                            Text(
                                text = dev.thermalSensors.topSensorNames.joinToString("\n• ", prefix = "• "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
