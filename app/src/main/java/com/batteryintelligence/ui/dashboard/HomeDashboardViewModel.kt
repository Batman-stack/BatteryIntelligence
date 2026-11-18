package com.batteryintelligence.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.intelligence.ChargingIntelligenceEngine
import com.batteryintelligence.domain.model.BatterySampleRecord
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.repository.BatteryTelemetryRepository
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class DashboardTimeWindow(val label: String, val durationMs: Long) {
    ONE_HOUR("1h", 3_600_000L),
    SIX_HOURS("6h", 6L * 3_600_000L),
    TWENTY_FOUR_HOURS("24h", 24L * 3_600_000L)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeDashboardViewModel @Inject constructor(
    private val batteryTelemetryRepository: BatteryTelemetryRepository,
    chargingSessionRepository: ChargingSessionRepository,
    preferencesRepository: PreferencesRepository,
    private val chargingIntelligenceEngine: ChargingIntelligenceEngine
) : ViewModel() {

    private val _selectedWindow = MutableStateFlow(DashboardTimeWindow.ONE_HOUR)
    val selectedWindow: StateFlow<DashboardTimeWindow> = _selectedWindow.asStateFlow()

    val preferences: StateFlow<MonitoringPreferences> = preferencesRepository
        .preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MonitoringPreferences()
        )

    val liveState: StateFlow<ChargingIntelligenceState> = combine(
        batteryTelemetryRepository.observeLiveTelemetry(samplingIntervalMs = 2_000L),
        preferencesRepository.preferences,
        chargingSessionRepository.observeHealthReport()
    ) { snapshot, prefs, health ->
        chargingIntelligenceEngine.processSnapshot(
            snapshot = snapshot,
            preferences = prefs,
            effectiveCapacityMah = health.estimatedFullCapacityMah ?: health.designCapacityMah
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChargingIntelligenceState()
    )

    val recentSamples: StateFlow<List<BatterySampleRecord>> = _selectedWindow
        .flatMapLatest { window ->
            batteryTelemetryRepository.observeRecentSamples(
                sinceTimestampMs = System.currentTimeMillis() - window.durationMs
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun selectTimeWindow(window: DashboardTimeWindow) {
        _selectedWindow.value = window
    }
}
