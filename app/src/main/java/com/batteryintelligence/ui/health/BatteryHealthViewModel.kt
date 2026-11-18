package com.batteryintelligence.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.model.BatteryHealthReport
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class BatteryHealthViewModel @Inject constructor(
    chargingSessionRepository: ChargingSessionRepository
) : ViewModel() {

    val healthReport: StateFlow<BatteryHealthReport?> = chargingSessionRepository
        .observeHealthReport()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val capacityTrendSessions: StateFlow<List<ChargingSession>> = chargingSessionRepository
        .observeCapacityTrendSessions(limit = 20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )
}
