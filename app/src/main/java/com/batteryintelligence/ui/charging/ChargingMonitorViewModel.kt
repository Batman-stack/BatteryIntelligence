package com.batteryintelligence.ui.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChargingMonitorViewModel @Inject constructor(
    chargingSessionRepository: ChargingSessionRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val activeSession: StateFlow<ChargingSession?> = chargingSessionRepository
        .observeActiveSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val recentSessions: StateFlow<List<ChargingSession>> = chargingSessionRepository
        .observeChargingSessions(limit = 15)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val preferences: StateFlow<MonitoringPreferences> = preferencesRepository
        .preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MonitoringPreferences()
        )

    fun updateTargetChargeAlert(percent: Int?) {
        viewModelScope.launch {
            preferencesRepository.updateTargetChargeAlert(percent)
        }
    }
}
