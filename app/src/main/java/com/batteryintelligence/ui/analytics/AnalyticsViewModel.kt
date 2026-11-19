package com.batteryintelligence.ui.analytics

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batteryintelligence.domain.intelligence.AppBatteryAttributionCalculator
import com.batteryintelligence.domain.model.AppUsageDetail
import com.batteryintelligence.domain.model.AppUsageSortMode
import com.batteryintelligence.domain.model.DailyBatteryRollup
import com.batteryintelligence.domain.model.OvernightLog
import com.batteryintelligence.domain.model.Rolling24HourUsageReport
import com.batteryintelligence.domain.repository.AppUsageRepository
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.OvernightLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val appUsageRepository: AppUsageRepository,
    overnightLogRepository: OvernightLogRepository,
    chargingSessionRepository: ChargingSessionRepository
) : ViewModel() {

    private val _hasUsagePermission = MutableStateFlow(appUsageRepository.hasUsageStatsPermission())
    val hasUsagePermission: StateFlow<Boolean> = _hasUsagePermission.asStateFlow()

    private val _isLoadingUsage = MutableStateFlow(false)
    val isLoadingUsage: StateFlow<Boolean> = _isLoadingUsage.asStateFlow()

    private val _rollingReport = MutableStateFlow<Rolling24HourUsageReport?>(null)
    val rollingReport: StateFlow<Rolling24HourUsageReport?> = _rollingReport.asStateFlow()

    private val _sortMode = MutableStateFlow(AppUsageSortMode.MOST_USED)
    val sortMode: StateFlow<AppUsageSortMode> = _sortMode.asStateFlow()

    private val _selectedApp = MutableStateFlow<AppUsageDetail?>(null)
    val selectedApp: StateFlow<AppUsageDetail?> = _selectedApp.asStateFlow()

    val sortedApps: StateFlow<List<AppUsageDetail>> = combine(
        _rollingReport,
        _sortMode
    ) { report, mode ->
        if (report == null) emptyList()
        else AppBatteryAttributionCalculator.sortApps(report.apps, mode)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

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

    fun selectSortMode(mode: AppUsageSortMode) {
        _sortMode.value = mode
    }

    fun selectAppForDetail(app: AppUsageDetail?) {
        _selectedApp.value = app
    }

    fun getAppIcon(packageName: String): Drawable? {
        return appUsageRepository.getCachedAppIcon(packageName)
    }

    fun refreshUsageStats() {
        val granted = appUsageRepository.hasUsageStatsPermission()
        _hasUsagePermission.value = granted
        if (!granted) {
            _rollingReport.value = null
            _selectedApp.value = null
            _isLoadingUsage.value = false
            return
        }
        viewModelScope.launch {
            _isLoadingUsage.value = true
            val report = runCatching {
                appUsageRepository.getRolling24HourUsageReport()
            }.getOrNull()
            _rollingReport.value = report
            _isLoadingUsage.value = false
        }
    }
}
