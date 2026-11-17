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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ChargingMonitorScreen(
    modifier: Modifier = Modifier,
    viewModel: ChargingMonitorViewModel = hiltViewModel()
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val recentSessions by viewModel.recentSessions.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSession != null) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (activeSession != null) "Active Charging Session" else "Not Currently Charging",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (activeSession != null) {
                        val session = activeSession!!
                        Text(text = "Source: ${session.chargingSource}")
                        Text(text = "Started at: ${session.startLevelPercent}%")
                        Text(text = "Current/Last Level: ${session.endLevelPercent ?: session.startLevelPercent}%")
                        session.peakPowerWatts?.let { Text(text = String.format("Peak Power: %.1f W", it)) }
                        session.peakTemperatureCelsius?.let { Text(text = String.format("Peak Temp: %.1f °C", it)) }
                    } else {
                        Text(
                            text = "Target Charge Alarm: ${prefs.targetChargeAlertPercent ?: "--"}%",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            Text(text = "Recent Sessions", style = MaterialTheme.typography.titleMedium)
        }

        if (recentSessions.isEmpty()) {
            item {
                Text(
                    text = "No recorded charging sessions yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(recentSessions) { session ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${session.startLevelPercent}% → ${session.endLevelPercent ?: "?"}%",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = session.chargingSource.name,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        session.estimatedCapacityMah?.let {
                            Text(
                                text = String.format("Est. Capacity: %.0f mAh", it),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        session.chargingEfficiencyPercent?.let {
                            Text(
                                text = String.format("Efficiency: %.1f%%", it),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
