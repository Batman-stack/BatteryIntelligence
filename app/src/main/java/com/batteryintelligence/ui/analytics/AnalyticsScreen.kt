package com.batteryintelligence.ui.analytics

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val usageList by viewModel.appUsage.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasUsagePermission.collectAsStateWithLifecycle()
    val overnightLogs by viewModel.overnightLogs.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "App Power Attribution (Past 24h)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (!hasPermission) {
                        Text(
                            text = "Usage Access permission is required to analyze per-app power usage. Grant it in Android Settings > Apps > Special App Access > Usage Access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (usageList.isEmpty()) {
                        Text(
                            text = "No foreground usage recorded in the past 24 hours.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (hasPermission && usageList.isNotEmpty()) {
            items(usageList) { app ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = app.appName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = String.format("%.1f%%", app.estimatedSharePercent),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (app.estimatedSharePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Foreground: ${app.foregroundTimeMs / 60_000} mins",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Text(text = "Overnight Drain Logs", style = MaterialTheme.typography.titleMedium)
        }

        if (overnightLogs.isEmpty()) {
            item {
                Text(
                    text = "No overnight idle sessions recorded yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(overnightLogs) { log ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = "Night: ${log.nightDateIso}", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Drop: ${log.batteryDropPercent ?: 0}% (${log.startLevelPercent ?: 0}% → ${log.endLevelPercent ?: 0}%)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        log.drainRatePercentPerHour?.let {
                            Text(text = String.format("Drain Rate: %.2f%% / hr", it), style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "Plugged in: ${if (log.wasPluggedIn) "Yes" else "No"} | Samples: ${log.sampleCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
