package com.batteryintelligence.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Monitoring Service", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Foreground Service", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Keep live telemetry monitoring active via a foreground notification.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.foregroundServiceEnabled,
                            onCheckedChange = { viewModel.setForegroundServiceEnabled(it) }
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Smart Notifications", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "Alert when thermal threshold or target charge is reached.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = prefs.alertsEnabled,
                            onCheckedChange = { viewModel.setAlertsEnabled(it) }
                        )
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Thresholds & Overrides", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = "Sampling Interval: ${prefs.samplingIntervalSeconds} seconds")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "High Temp Alert: ${prefs.highTempAlertCelsius ?: "--"} °C")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Target Charge Alarm: ${prefs.targetChargeAlertPercent ?: "--"}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Low Battery Alert: ${prefs.lowBatteryAlertPercent ?: "--"}%")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Design Capacity Override: ${prefs.userDesignCapacityMahOverride?.let { "$it mAh" } ?: "Auto (BatteryManager)"}"
                    )
                }
            }
        }
    }
}
