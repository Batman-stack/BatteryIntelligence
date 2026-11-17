package com.batteryintelligence.ui.health

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
fun BatteryHealthScreen(
    modifier: Modifier = Modifier,
    viewModel: BatteryHealthViewModel = hiltViewModel()
) {
    val report by viewModel.healthReport.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "State of Health",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = report?.stateOfHealthPercent?.let { "$it%" } ?: "--",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Source: ${report?.stateOfHealthSource?.name ?: "Unknown"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = "Capacity & Cycles", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Design Capacity: ${report?.designCapacityMah?.let { String.format("%.0f mAh", it) } ?: "--"} (${report?.designCapacitySource?.name ?: "Unknown"})"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Estimated Full Capacity: ${report?.estimatedFullCapacityMah?.let { String.format("%.0f mAh", it) } ?: "--"}"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cycle Count: ${report?.cycleCount ?: "--"} (${report?.cycleCountSource?.name ?: "Unknown"})"
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Evaluated Sessions: ${report?.evaluatedSessionsCount ?: 0}"
                    )
                }
            }
        }
    }
}
