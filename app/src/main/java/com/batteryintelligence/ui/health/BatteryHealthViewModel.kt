package com.batteryintelligence.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.model.BatteryHealthReport
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.DeviceIntelligenceSnapshot
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.model.OvernightLog
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.DeviceIntelligenceRepository
import com.batteryintelligence.domain.repository.OvernightLogRepository
import com.batteryintelligence.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BatteryHealthViewModel @Inject constructor(
    chargingSessionRepository: ChargingSessionRepository,
    overnightLogRepository: OvernightLogRepository,
    preferencesRepository: PreferencesRepository,
    private val deviceIntelligenceRepository: DeviceIntelligenceRepository
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

    val recentOvernightLogs: StateFlow<List<OvernightLog>> = overnightLogRepository
        .observeRecentLogs(limit = 5)
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

    private val _deviceIntelligence = MutableStateFlow<DeviceIntelligenceSnapshot?>(null)
    val deviceIntelligence: StateFlow<DeviceIntelligenceSnapshot?> = _deviceIntelligence.asStateFlow()

    init {
        refreshDeviceIntelligence()
    }

    fun refreshDeviceIntelligence() {
        viewModelScope.launch {
            _deviceIntelligence.value = runCatching {
                deviceIntelligenceRepository.readDeviceIntelligence()
            }.getOrNull()
        }
    }
}
