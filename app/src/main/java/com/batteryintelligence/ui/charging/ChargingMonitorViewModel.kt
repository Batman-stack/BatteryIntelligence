package com.batteryintelligence.ui.charging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.intelligence.ChargingIntelligenceEngine
import com.batteryintelligence.domain.model.ChargerSourceBenchmark
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.ChargingSessionSample
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.repository.BatteryTelemetryRepository
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.PreferencesRepository
import com.batteryintelligence.service.ServiceLifecycleController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChargingMonitorViewModel @Inject constructor(
    private val chargingSessionRepository: ChargingSessionRepository,
    private val batteryTelemetryRepository: BatteryTelemetryRepository,
    private val preferencesRepository: PreferencesRepository,
    private val chargingIntelligenceEngine: ChargingIntelligenceEngine,
    private val serviceLifecycleController: ServiceLifecycleController
) : ViewModel() {

    val activeSession: StateFlow<ChargingSession?> = chargingSessionRepository
        .observeActiveSession()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val activeSessionSamples: StateFlow<List<ChargingSessionSample>> = activeSession
        .flatMapLatest { session ->
            if (session == null) flowOf(emptyList())
            else chargingSessionRepository.observeSessionSamples(session.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val liveIntelligence: StateFlow<ChargingIntelligenceState> = combine(
        batteryTelemetryRepository.observeLiveTelemetry(samplingIntervalMs = 2_000L),
        preferencesRepository.preferences,
        chargingSessionRepository.observeHealthReport()
    ) { snapshot, prefs, health ->
        val state = chargingIntelligenceEngine.processSnapshot(
            snapshot = snapshot,
            preferences = prefs,
            effectiveCapacityMah = health.estimatedFullCapacityMah ?: health.designCapacityMah
        )
        if (!prefs.foregroundServiceEnabled) {
            chargingSessionRepository.processSnapshotForSession(snapshot, state)
        }
        state
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChargingIntelligenceState()
    )

    val recentSessions: StateFlow<List<ChargingSession>> = chargingSessionRepository
        .observeChargingSessions(limit = 20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val benchmarks: StateFlow<List<ChargerSourceBenchmark>> = chargingSessionRepository
        .observeChargerBenchmarks(limit = 50)
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
            val clamped = percent?.coerceIn(50, 100)
            preferencesRepository.updateTargetChargeAlert(clamped)
            serviceLifecycleController.syncAlarmConfigIfEnabled(
                preferences.value.copy(targetChargeAlertPercent = clamped)
            )
        }
    }
}
