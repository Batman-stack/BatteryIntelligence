package com.batteryintelligence.domain.repository

import com.batteryintelligence.domain.model.MonitoringPreferences
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<MonitoringPreferences>

    suspend fun updateSamplingInterval(seconds: Int)

    suspend fun setForegroundServiceEnabled(enabled: Boolean)

    suspend fun setAlertsEnabled(enabled: Boolean)

    suspend fun updateHighTempAlert(celsius: Float?)

    suspend fun updateTargetChargeAlert(percent: Int?)

    suspend fun updateLowBatteryAlert(percent: Int?)

    suspend fun updateDesignCapacityOverride(capacityMah: Double?)
}