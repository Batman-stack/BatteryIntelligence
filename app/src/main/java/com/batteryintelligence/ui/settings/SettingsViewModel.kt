package com.batteryintelligence.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.model.AppThemeMode
import com.batteryintelligence.domain.model.CapacityUnit
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.model.SpeedTierThresholds
import com.batteryintelligence.domain.model.TemperatureUnit
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.PreferencesRepository
import com.batteryintelligence.service.ServiceLifecycleController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val serviceLifecycleController: ServiceLifecycleController,
    private val chargingSessionRepository: ChargingSessionRepository
) : ViewModel() {

    val preferences: StateFlow<MonitoringPreferences> = preferencesRepository
        .preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MonitoringPreferences()
        )

    private val _serviceNotice = MutableStateFlow<String?>(null)
    val serviceNotice: StateFlow<String?> = _serviceNotice.asStateFlow()

    private val _pendingCsvExport = MutableStateFlow<String?>(null)
    val pendingCsvExport: StateFlow<String?> = _pendingCsvExport.asStateFlow()

    fun dismissServiceNotice() {
        _serviceNotice.value = null
    }

    fun consumePendingCsvExport() {
        _pendingCsvExport.value = null
    }

    fun updateSamplingInterval(seconds: Int) {
        viewModelScope.launch { preferencesRepository.updateSamplingInterval(seconds) }
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val applied = serviceLifecycleController.setMonitoringServiceEnabled(enabled)
            if (!applied && enabled) {
                preferencesRepository.setForegroundServiceEnabled(false)
                _serviceNotice.value = "Android blocked starting the foreground service right now. Check notification/battery optimization permissions."
            }
        }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAlertsEnabled(enabled)
            serviceLifecycleController.syncAlarmConfigIfEnabled(
                preferences.value.copy(alertsEnabled = enabled)
            )
        }
    }

    fun setDisconnectAlertEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDisconnectAlertEnabled(enabled)
        }
    }

    fun updateHighTempAlert(celsius: Float?) {
        viewModelScope.launch {
            preferencesRepository.updateHighTempAlert(celsius)
            serviceLifecycleController.syncAlarmConfigIfEnabled(
                preferences.value.copy(highTempAlertCelsius = celsius)
            )
        }
    }

    fun updateThermalSlopeAlert(cPerMin: Float?) {
        viewModelScope.launch { preferencesRepository.updateThermalSlopeAlert(cPerMin) }
    }

    fun updateTargetChargeAlert(percent: Int?) {
        viewModelScope.launch {
            val clamped = percent?.coerceIn(50, 100)
            preferencesRepository.updateTargetChargeAlert(clamped)
            serviceLifecycleController.syncAlarmConfigIfEnabled(
                preferences.value.copy(targetChargeAlertPercent = clamped)
            )
        }
    }

    fun updateLowBatteryAlert(percent: Int?) {
        viewModelScope.launch { preferencesRepository.updateLowBatteryAlert(percent) }
    }

    fun updateDesignCapacityOverride(capacityMah: Double?) {
        viewModelScope.launch { preferencesRepository.updateDesignCapacityOverride(capacityMah) }
    }

    fun updateSpeedTierThresholds(thresholds: SpeedTierThresholds) {
        viewModelScope.launch { preferencesRepository.updateSpeedTierThresholds(thresholds) }
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { preferencesRepository.updateThemeMode(mode) }
    }

    fun updateTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch { preferencesRepository.updateTemperatureUnit(unit) }
    }

    fun updateCapacityUnit(unit: CapacityUnit) {
        viewModelScope.launch { preferencesRepository.updateCapacityUnit(unit) }
    }

    fun setUseDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setUseDynamicColor(enabled) }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setHapticsEnabled(enabled) }
    }

    fun generateCsvExport() {
        viewModelScope.launch {
            _pendingCsvExport.value = runCatching {
                chargingSessionRepository.buildCsvExportReport()
            }.getOrNull()
        }
    }

    fun pruneOldHistory(days: Int) {
        viewModelScope.launch {
            val deleted = runCatching {
                chargingSessionRepository.pruneDataOlderThanDays(days)
            }.getOrDefault(0)
            _serviceNotice.value = "Removed $deleted telemetry records older than $days days."
        }
    }
}
