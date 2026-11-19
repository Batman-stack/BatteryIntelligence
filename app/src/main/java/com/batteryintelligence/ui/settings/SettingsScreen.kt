package com.batteryintelligence.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryintelligence.domain.model.AppThemeMode
import com.batteryintelligence.domain.model.CapacityUnit
import com.batteryintelligence.domain.model.SpeedTierThresholds
import com.batteryintelligence.domain.model.TemperatureUnit
import com.batteryintelligence.ui.components.TactileCard
import com.batteryintelligence.ui.components.TactilePillButton
import com.batteryintelligence.ui.components.UnitFormatters
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val serviceNotice by viewModel.serviceNotice.collectAsStateWithLifecycle()
    val pendingCsvExport by viewModel.pendingCsvExport.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val semantic = LocalBatterySemanticColors.current

    var showCapacityDialog by remember { mutableStateOf(false) }
    var showSpeedTierDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setForegroundServiceEnabled(true)
        }
    }

    LaunchedEffect(pendingCsvExport) {
        val csv = pendingCsvExport ?: return@LaunchedEffect
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "Battery Intelligence Telemetry Export (CSV)")
            putExtra(Intent.EXTRA_TEXT, csv)
        }
        runCatching {
            context.startActivity(Intent.createChooser(shareIntent, "Export Battery Telemetry CSV"))
        }
        viewModel.consumePendingCsvExport()
    }

    if (serviceNotice != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissServiceNotice() },
            title = { Text("Notice") },
            text = { Text(serviceNotice.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissServiceNotice() }) {
                    Text("OK", color = semantic.emeraldText)
                }
            }
        )
    }

    if (showCapacityDialog) {
        DesignCapacityOverrideDialog(
            currentOverride = prefs.userDesignCapacityMahOverride,
            onDismiss = { showCapacityDialog = false },
            onSave = { value ->
                viewModel.updateDesignCapacityOverride(value)
                showCapacityDialog = false
            }
        )
    }

    if (showSpeedTierDialog) {
        SpeedThresholdsDialog(
            current = prefs.speedTierThresholds,
            onDismiss = { showSpeedTierDialog = false },
            onSave = { thresholds ->
                viewModel.updateSpeedTierThresholds(thresholds)
                showSpeedTierDialog = false
            }
        )
    }

    if (showPrivacyDialog) {
        LocalPrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Monitoring Service
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(text = "Monitoring Service", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Always-On Foreground Service", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Continuously captures high-resolution charging curves and fires smart alarms even when closed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.foregroundServiceEnabled,
                        onCheckedChange = { enabled ->
                            if (prefs.hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                                if (!granted) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    return@Switch
                                }
                            }
                            viewModel.setForegroundServiceEnabled(enabled)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Sampling Interval (${prefs.samplingIntervalSeconds}s)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 5, 10, 15, 30).forEach { seconds ->
                        FilterChip(
                            selected = prefs.samplingIntervalSeconds == seconds,
                            onClick = { viewModel.updateSamplingInterval(seconds) },
                            label = { Text("${seconds}s") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = semantic.mintSurface,
                                selectedLabelColor = semantic.emeraldText
                            )
                        )
                    }
                }
            }
        }

        // 2. Smart Charging & Thermal Alerts
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Smart Charging & Thermal Alerts", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Notify on custom target, rapid heating slope, high temperature, or low battery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.alertsEnabled,
                        onCheckedChange = { viewModel.setAlertsEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Charger Disconnected Alert", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Alert if power disconnects unexpectedly before reaching target level.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.disconnectAlertEnabled,
                        onCheckedChange = { viewModel.setDisconnectAlertEnabled(it) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val targetPct = (prefs.targetChargeAlertPercent ?: 80).coerceIn(50, 100)
                SettingSliderRow(
                    label = "Target Charge Alarm",
                    valueLabel = "$targetPct%",
                    value = targetPct.toFloat(),
                    range = 50f..100f,
                    steps = 9,
                    onValueChange = { viewModel.updateTargetChargeAlert(it.roundToInt()) }
                )

                val highTemp = (prefs.highTempAlertCelsius ?: 42.0f).coerceIn(36.0f, 48.0f)
                SettingSliderRow(
                    label = "High Temperature Threshold",
                    valueLabel = UnitFormatters.formatTemperature(highTemp, prefs.temperatureUnit),
                    value = highTemp,
                    range = 36f..48f,
                    steps = 23,
                    onValueChange = { viewModel.updateHighTempAlert((it * 2).roundToInt() / 2f) }
                )

                val slopeLimit = (prefs.thermalSlopeAlertCPerMin ?: 1.2f).coerceIn(0.5f, 2.5f)
                SettingSliderRow(
                    label = "Thermal Slope Alert",
                    valueLabel = String.format(Locale.US, "+%.1f °C/min", slopeLimit),
                    value = slopeLimit,
                    range = 0.5f..2.5f,
                    steps = 19,
                    onValueChange = { viewModel.updateThermalSlopeAlert((it * 10).roundToInt() / 10f) }
                )

                val lowBattery = (prefs.lowBatteryAlertPercent ?: 20).coerceIn(10, 35)
                SettingSliderRow(
                    label = "Low Battery Warning",
                    valueLabel = "$lowBattery%",
                    value = lowBattery.toFloat(),
                    range = 10f..35f,
                    steps = 4,
                    onValueChange = { viewModel.updateLowBatteryAlert(it.roundToInt()) }
                )
            }
        }

        // 3. Calibration & Speed Tiers
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(text = "Calibration & Speed Tiers", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Design Capacity: ${
                        prefs.userDesignCapacityMahOverride?.let {
                            "${UnitFormatters.formatCapacity(it, prefs.capacityUnit)} (Manual)"
                        } ?: "Auto (Device Fuel Gauge / Sysfs)"
                    }",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = { showCapacityDialog = true }) {
                    Text("Set Design Capacity Override", color = semantic.emeraldText)
                }

                Spacer(modifier = Modifier.height(12.dp))
                val t = prefs.speedTierThresholds
                Text(
                    text = String.format(
                        Locale.US,
                        "Speed Thresholds: Normal ≥%.1fW • Fast ≥%.1fW • Very Fast ≥%.1fW",
                        t.normalMinWatts,
                        t.fastMinWatts,
                        t.veryFastMinWatts
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(onClick = { showSpeedTierDialog = true }) {
                    Text("Configure Speed Wattage Tiers", color = semantic.emeraldText)
                }
            }
        }

        // 4. Units, Appearance & Tactile Feedback
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(text = "Units, Appearance & Haptics", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Temperature Unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemperatureUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = prefs.temperatureUnit == unit,
                            onClick = { viewModel.updateTemperatureUnit(unit) },
                            label = { Text("${unit.name.lowercase().replaceFirstChar { it.uppercase() }} (${unit.symbol})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = semantic.mintSurface,
                                selectedLabelColor = semantic.emeraldText
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Capacity Unit",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CapacityUnit.entries.forEach { unit ->
                        FilterChip(
                            selected = prefs.capacityUnit == unit,
                            onClick = { viewModel.updateCapacityUnit(unit) },
                            label = { Text(unit.symbol) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = semantic.mintSurface,
                                selectedLabelColor = semantic.emeraldText
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.updateThemeMode(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = semantic.mintSurface,
                                selectedLabelColor = semantic.emeraldText
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Material You Dynamic Color", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Off by default to keep Emerald & Pure White contrast.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.useDynamicColor,
                        onCheckedChange = { viewModel.setUseDynamicColor(it) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Tactile Haptics", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Subtle haptic clicks on cards and threshold controls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = prefs.hapticsEnabled,
                        onCheckedChange = { viewModel.setHapticsEnabled(it) }
                    )
                }
            }
        }

        // 5. Data Export & Database Storage Management
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(text = "Local Data & Storage", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "All charging sessions, thermal curves, and app usage summaries stay strictly on-device in Room SQLite.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                TactilePillButton(
                    text = "Export Charging Sessions (CSV)",
                    onClick = { viewModel.generateCsvExport() },
                    emphasized = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.pruneOldHistory(30) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Prune >30d", color = semantic.emeraldText)
                    }
                    OutlinedButton(
                        onClick = { viewModel.pruneOldHistory(90) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Prune >90d", color = semantic.emeraldText)
                    }
                }
            }
        }

        // 6. Local-First Privacy Policy & Release Info
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(text = "Privacy & Release Info", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Battery Intelligence v3.0.0 — Personal Release. Zero cloud accounts, zero ads, zero analytics SDKs, zero hidden/root APIs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showPrivacyDialog = true }) {
                    Text("View Local-First Privacy & Sensor Policy", color = semantic.emeraldText)
                }
            }
        }
    }
}

@Composable
private fun LocalPrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Local-First Privacy & Sensor Policy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "100% On-Device Processing: Battery Intelligence never requests INTERNET permission and never transmits battery telemetry, app usage history, or device diagnostics off your phone.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Usage Access (Optional): PACKAGE_USAGE_STATS is used exclusively to reconstruct your rolling 24-hour foreground app sessions and timeline inside the Analytics tab.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Public Android APIs Only: All electrical, thermal, memory, storage, and display diagnostics are read strictly from official Android 12–17 APIs and readable kernel files. Unavailable metrics are shown honestly as Unavailable.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = LocalBatterySemanticColors.current.emeraldText)
            }
        }
    )
}

@Composable
private fun SettingSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    val semantic = LocalBatterySemanticColors.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.titleSmall,
                color = semantic.emeraldText
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = semantic.emeraldStroke,
                activeTrackColor = semantic.emeraldStroke
            )
        )
    }
}

@Composable
private fun DesignCapacityOverrideDialog(
    currentOverride: Double?,
    onDismiss: () -> Unit,
    onSave: (Double?) -> Unit
) {
    var input by remember { mutableStateOf(currentOverride?.toInt()?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Design Capacity Override (mAh)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Enter your phone's rated typical battery capacity (e.g., 5000). Leave empty to auto-detect.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit) },
                    label = { Text("Capacity (mAh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(null) }) {
                Text("Use Auto")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = input.toDoubleOrNull()?.takeIf { it in 500.0..25_000.0 }
                    onSave(parsed)
                }
            ) {
                Text("Save", color = LocalBatterySemanticColors.current.emeraldText)
            }
        }
    )
}

@Composable
private fun SpeedThresholdsDialog(
    current: SpeedTierThresholds,
    onDismiss: () -> Unit,
    onSave: (SpeedTierThresholds) -> Unit
) {
    var normalInput by remember { mutableStateOf(current.normalMinWatts.toString()) }
    var fastInput by remember { mutableStateOf(current.fastMinWatts.toString()) }
    var veryFastInput by remember { mutableStateOf(current.veryFastMinWatts.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Battery-Side Speed Tiers (W)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = normalInput,
                    onValueChange = { normalInput = it },
                    label = { Text("Normal Min (W)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = fastInput,
                    onValueChange = { fastInput = it },
                    label = { Text("Fast Min (W)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                OutlinedTextField(
                    value = veryFastInput,
                    onValueChange = { veryFastInput = it },
                    label = { Text("Very Fast Min (W)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onSave(SpeedTierThresholds()) }) {
                Text("Reset Defaults")
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val n = normalInput.toDoubleOrNull() ?: current.normalMinWatts
                    val f = fastInput.toDoubleOrNull() ?: current.fastMinWatts
                    val vf = veryFastInput.toDoubleOrNull() ?: current.veryFastMinWatts
                    onSave(SpeedTierThresholds(n, f, vf))
                }
            ) {
                Text("Apply", color = LocalBatterySemanticColors.current.emeraldText)
            }
        }
    )
}
