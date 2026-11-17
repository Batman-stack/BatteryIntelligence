package com.batteryintelligence.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.model.BatterySampleRecord
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.repository.BatteryTelemetryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeDashboardViewModel @Inject constructor(
    batteryTelemetryRepository: BatteryTelemetryRepository
) : ViewModel() {

    val liveSnapshot: StateFlow<BatterySnapshot?> = batteryTelemetryRepository
        .observeLiveTelemetry(samplingIntervalMs = 2_000L)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val recentSamples: StateFlow<List<BatterySampleRecord>> = batteryTelemetryRepository
        .observeRecentSamples(sinceTimestampMs = System.currentTimeMillis() - 3_600_000L)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
