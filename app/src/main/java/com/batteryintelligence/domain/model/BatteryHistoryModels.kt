package com.batteryintelligence.domain.model

data class BatterySampleRecord(
    val id: Long = 0L,
    val timestampMs: Long,
    val levelPercent: Int?,
    val chargingStatus: ChargingStatus,
    val chargingSource: ChargingSource,
    val temperatureCelsius: Float?,
    val voltageMv: Int?,
    val currentNowMa: Double?,
    val powerWatts: Double?,
    val chargeCounterUah: Long?
)

data class ChargingSession(
    val id: Long = 0L,
    val startTimeMs: Long,
    val endTimeMs: Long?,
    val startLevelPercent: Int,
    val endLevelPercent: Int?,
    val chargingSource: ChargingSource,
    val peakCurrentMa: Double?,
    val peakPowerWatts: Double?,
    val peakTemperatureCelsius: Float?,
    val averageTemperatureCelsius: Float?,
    val chargeAddedUah: Long?,
    val estimatedCapacityMah: Double?,
    val isActive: Boolean,
    val minTemperatureCelsius: Float? = null,
    val energyAddedWh: Double? = null,
    val chargingEfficiencyPercent: Double? = null
)

data class OvernightLog(
    val nightDateIso: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val startLevelPercent: Int?,
    val endLevelPercent: Int?,
    val batteryDropPercent: Int?,
    val drainRatePercentPerHour: Float?,
    val chargeDrainedUah: Long?,
    val minTemperatureCelsius: Float?,
    val maxTemperatureCelsius: Float?,
    val wasPluggedIn: Boolean,
    val sampleCount: Int
)

data class BatteryHealthReport(
    val designCapacityMah: Double?,
    val designCapacitySource: MetricSource,
    val estimatedFullCapacityMah: Double?,
    val stateOfHealthPercent: Int?,
    val stateOfHealthSource: MetricSource,
    val cycleCount: Int?,
    val cycleCountSource: MetricSource,
    val evaluatedSessionsCount: Int
)

data class AppPowerUsageSummary(
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long,
    val totalVisibleTimeMs: Long,
    val estimatedSharePercent: Float,
    val lastTimeUsedMs: Long
)

data class MonitoringPreferences(
    val samplingIntervalSeconds: Int = 5,
    val foregroundServiceEnabled: Boolean = false,
    val alertsEnabled: Boolean = true,
    val highTempAlertCelsius: Float? = 42.0f,
    val targetChargeAlertPercent: Int? = 80,
    val lowBatteryAlertPercent: Int? = 20,
    val userDesignCapacityMahOverride: Double? = null
)