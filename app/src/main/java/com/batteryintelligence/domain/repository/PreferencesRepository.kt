package com.batteryintelligence.domain.repository

import com.batteryintelligence.domain.model.AppThemeMode
import com.batteryintelligence.domain.model.CapacityUnit
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.model.SpeedTierThresholds
import com.batteryintelligence.domain.model.TemperatureUnit
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    val preferences: Flow<MonitoringPreferences>

    suspend fun updateSamplingInterval(seconds: Int)

    suspend fun setForegroundServiceEnabled(enabled: Boolean)

    suspend fun setAlertsEnabled(enabled: Boolean)

    suspend fun setDisconnectAlertEnabled(enabled: Boolean)

    suspend fun updateHighTempAlert(celsius: Float?)

    suspend fun updateThermalSlopeAlert(cPerMin: Float?)

    suspend fun updateTargetChargeAlert(percent: Int?)

    suspend fun updateLowBatteryAlert(percent: Int?)

    suspend fun updateDesignCapacityOverride(capacityMah: Double?)

    suspend fun updateSpeedTierThresholds(thresholds: SpeedTierThresholds)

    suspend fun updateThemeMode(themeMode: AppThemeMode)

    suspend fun updateTemperatureUnit(unit: TemperatureUnit)

    suspend fun updateCapacityUnit(unit: CapacityUnit)

    suspend fun setUseDynamicColor(enabled: Boolean)

    suspend fun setHapticsEnabled(enabled: Boolean)
}