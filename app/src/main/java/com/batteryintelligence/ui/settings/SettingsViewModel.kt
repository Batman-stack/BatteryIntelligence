package com.batteryintelligence.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<MonitoringPreferences> = preferencesRepository
        .preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MonitoringPreferences()
        )

    fun updateSamplingInterval(seconds: Int) {
        viewModelScope.launch { preferencesRepository.updateSamplingInterval(seconds) }
    }

    fun setForegroundServiceEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setForegroundServiceEnabled(enabled) }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAlertsEnabled(enabled) }
    }

    fun updateHighTempAlert(celsius: Float?) {
        viewModelScope.launch { preferencesRepository.updateHighTempAlert(celsius) }
    }

    fun updateTargetChargeAlert(percent: Int?) {
        viewModelScope.launch { preferencesRepository.updateTargetChargeAlert(percent) }
    }

    fun updateLowBatteryAlert(percent: Int?) {
        viewModelScope.launch { preferencesRepository.updateLowBatteryAlert(percent) }
    }

    fun updateDesignCapacityOverride(capacityMah: Double?) {
        viewModelScope.launch { preferencesRepository.updateDesignCapacityOverride(capacityMah) }
    }
}
