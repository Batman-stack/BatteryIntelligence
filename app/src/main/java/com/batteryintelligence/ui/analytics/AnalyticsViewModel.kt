package com.batteryintelligence.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.model.AppPowerUsageSummary
import com.batteryintelligence.domain.model.DailyBatteryRollup
import com.batteryintelligence.domain.model.OvernightLog
import com.batteryintelligence.domain.repository.AppUsageRepository
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.OvernightLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val appUsageRepository: AppUsageRepository,
    overnightLogRepository: OvernightLogRepository,
    chargingSessionRepository: ChargingSessionRepository
) : ViewModel() {

    private val _appUsage = MutableStateFlow<List<AppPowerUsageSummary>>(emptyList())
    val appUsage: StateFlow<List<AppPowerUsageSummary>> = _appUsage.asStateFlow()

    private val _hasUsagePermission = MutableStateFlow(appUsageRepository.hasUsageStatsPermission())
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    val overnightLogs: StateFlow<List<OvernightLog>> = overnightLogRepository
        .observeRecentLogs(limit = 14)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val dailyRollups: StateFlow<List<DailyBatteryRollup>> = chargingSessionRepository
        .observeDailyRollups(limit = 14)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        refreshUsageStats()
    }

    fun refreshUsageStats() {
        _hasUsagePermission.value = appUsageRepository.hasUsageStatsPermission()
        if (_hasUsagePermission.value) {
            viewModelScope.launch {
                val now = System.currentTimeMillis()
                val windowStart = now - (24 * 3_600_000L)
                _appUsage.value = appUsageRepository.getAppUsageAttribution(windowStart, now)
            }
        }
    }
}
