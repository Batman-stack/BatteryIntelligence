package com.batteryintelligence.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.batteryintelligence.domain.model.MetricSource
import com.batteryintelligence.ui.components.MetricSourceTag
import com.batteryintelligence.ui.components.MetricTile
import com.batteryintelligence.ui.components.TactileCard
import com.batteryintelligence.ui.components.TelemetryLineChart
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
    val semantic = LocalBatterySemanticColors.current

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
                            "Reported by device battery fuel gauge."
                        MetricSource.SESSION_DERIVED ->
                            "Derived from ${report?.evaluatedSessionsCount ?: 0} charging sessions (≥20% charge delta)."
                        else ->
                            "Complete ≥20% charging sessions or verify Design Capacity in Settings to calculate SoH."
                    },
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
                    label = "Design Capacity",
                    value = report?.designCapacityMah?.let { String.format(Locale.US, "%.0f mAh", it) } ?: "--",
                    subtitle = report?.designCapacitySource?.displayLabel,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "Estimated Full",
                    value = report?.estimatedFullCapacityMah?.let { String.format(Locale.US, "%.0f mAh", it) } ?: "--",
                    subtitle = "${report?.evaluatedSessionsCount ?: 0} sessions evaluated",
                    modifier = Modifier.weight(1f)
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
                    Text(text = "Cycle Count", style = MaterialTheme.typography.titleMedium)
                    report?.cycleCountSource?.let { MetricSourceTag(source = it) }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = report?.cycleCount?.toString() ?: "Unavailable on this device",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Hardware cycle count requires Android 14+ OEM HAL support; design capacity can be overridden manually in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(onClick = onNavigateToSettings) {
                    Text("Adjust Design Capacity Override", color = semantic.emeraldText)
                }
            }
        }

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
    }
}
