package com.batteryintelligence.service

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.os.Build
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceLifecycleController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository
) {
    /**
     * Starts or stops BatteryMonitorService immediately and persists the user's preference.
     * Returns true if the requested state was applied cleanly.
     */
    suspend fun setMonitoringServiceEnabled(enabled: Boolean): Boolean {
        preferencesRepository.setForegroundServiceEnabled(enabled)
        return if (enabled) {
            val prefs = preferencesRepository.preferences.first()
            startMonitoring(prefs)
        } else {
            stopMonitoring()
            true
        }
    }

    fun startMonitoring(prefs: MonitoringPreferences): Boolean {
        return try {
            BatteryMonitorService.start(
                context = context,
                targetChargePercent = prefs.targetChargeAlertPercent ?: 80,
                highTempAlertCelsius = prefs.highTempAlertCelsius,
                alertsEnabled = prefs.alertsEnabled
            )
            true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                false
            } else {
                false
            }
        }
    }

    fun syncAlarmConfigIfEnabled(prefs: MonitoringPreferences) {
        if (!prefs.foregroundServiceEnabled) return
        try {
            BatteryMonitorService.updateAlarmConfig(
                context = context,
                targetChargePercent = prefs.targetChargeAlertPercent ?: 80,
                highTempAlertCelsius = prefs.highTempAlertCelsius,
                alertsEnabled = prefs.alertsEnabled
            )
        } catch (_: Exception) {
            // Safe no-op if OS blocks background FGS start; service also observes DataStore directly.
        }
    }

    fun stopMonitoring() {
        BatteryMonitorService.stop(context)
    }
}
