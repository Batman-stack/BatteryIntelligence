package com.batteryintelligence.ui.analytics

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryintelligence.ui.components.DailyRollupColumnChart
import com.batteryintelligence.ui.components.StatusPill
import com.batteryintelligence.ui.components.TactileCard
import com.batteryintelligence.ui.theme.LocalBatterySemanticColors
import java.util.Locale

@Composable
fun AnalyticsScreen(
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = hiltViewModel()
) {
    val usageList by viewModel.appUsage.collectAsStateWithLifecycle()
    val hasPermission by viewModel.hasUsagePermission.collectAsStateWithLifecycle()
    val overnightLogs by viewModel.overnightLogs.collectAsStateWithLifecycle()
    val dailyRollups by viewModel.dailyRollups.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val semantic = LocalBatterySemanticColors.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshUsageStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rollupLabels = remember(dailyRollups) {
        dailyRollups.takeLast(7).map { it.dateIso.takeLast(5) }
    }
    val rollupEnergyWh = remember(dailyRollups) {
        dailyRollups.takeLast(7).map { it.totalEnergyAddedWh }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Text(
                    text = "Daily Charging Energy (Last 7 Days)",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                DailyRollupColumnChart(
                    labels = rollupLabels,
                    values = rollupEnergyWh,
                    barColor = semantic.emeraldStroke,
                    unitSuffix = " Wh",
                    emptyMessage = "Complete a charging session to view daily energy rollups."
                )
            }
        }

        item {
            TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "App Power Attribution (24h)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (hasPermission) {
                        OutlinedButton(onClick = { viewModel.refreshUsageStats() }) {
                            Text("Refresh", color = semantic.emeraldText)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (!hasPermission) {
                    Text(
                        text = "Usage Access permission is required to estimate foreground app battery attribution.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = semantic.emeraldStroke,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Grant Usage Access")
                    }
                } else if (usageList.isEmpty()) {
                    Text(
                        text = "No foreground app usage recorded in the past 24 hours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        usageList.take(10).forEach { app ->
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = app.appName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f%%", app.estimatedSharePercent),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = semantic.emeraldText
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (app.estimatedSharePercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    color = semantic.emeraldStroke,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeCap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "Overnight Drain Logs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (overnightLogs.isEmpty()) {
            item {
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Text(
                        text = "No overnight drain logs recorded yet (evaluates 22:00–07:00 windows).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(overnightLogs, key = { it.nightDateIso }) { log ->
                TactileCard(containerColor = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = log.nightDateIso,
                            style = MaterialTheme.typography.titleSmall
                        )
                        StatusPill(
                            text = if (log.wasPluggedIn) "Plugged In" else "Standby",
                            textColor = if (log.wasPluggedIn) semantic.emeraldText else semantic.warmText,
                            containerColor = if (log.wasPluggedIn) semantic.mintSurface else semantic.warmContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "${log.startLevelPercent ?: "?"}% → ${log.endLevelPercent ?: "?"}% • " +
                            (log.drainRatePercentPerHour?.let { String.format(Locale.US, "%.2f%%/hr", it) } ?: "--"),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
