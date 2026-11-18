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
    val durationMs: Long = 0L,
    val chargingSource: ChargingSource,
    val avgCurrentMa: Double? = null,
    val peakCurrentMa: Double?,
    val avgPowerWatts: Double? = null,
    val peakPowerWatts: Double?,
    val peakTemperatureCelsius: Float?,
    val averageTemperatureCelsius: Float?,
    val minTemperatureCelsius: Float? = null,
    val peakThermalSlopeCPerMin: Float? = null,
    val chargeAddedUah: Long?,
    val energyAddedWh: Double? = null,
    val chargingEfficiencyPercent: Double? = null,
    val estimatedCapacityMah: Double?,
    val dominantSpeedTier: ChargingSpeedTier? = null,
    val ccCvTransitionPercent: Int? = null,
    val ccCvTransitionTimeMs: Long? = null,
    val terminationReason: ChargingTerminationReason = ChargingTerminationReason.ACTIVE,
    val initialEta100Ms: Long? = null,
    val etaError100Ms: Long? = null,
    val isActive: Boolean
) {
    val energyAddedMah: Double?
        get() = chargeAddedUah?.let { it / 1000.0 }
}

data class ChargingSessionSample(
    val id: Long = 0L,
    val sessionId: Long,
    val timestampMs: Long,
    val elapsedMs: Long,
    val levelPercent: Int?,
    val levelPrecisePercent: Float?,
    val voltageMv: Int?,
    val currentNowMa: Double?,
    val smoothedPowerWatts: Double?,
    val temperatureCelsius: Float?,
    val thermalSlopeCPerMin: Float?,
    val speedTier: ChargingSpeedTier,
    val chargingPhase: ChargingPhase,
    val chargeCounterUah: Long?
)

data class DailyBatteryRollup(
    val dateIso: String,
    val sessionCount: Int,
    val totalChargingDurationMs: Long,
    val totalChargeAddedMah: Double,
    val totalEnergyAddedWh: Double,
    val avgPowerWatts: Double?,
    val maxPowerWatts: Double?,
    val avgTemperatureCelsius: Float?,
    val maxTemperatureCelsius: Float?,
    val totalDischargePercent: Int,
    val avgOvernightDrainPctPerHr: Float?,
    val estimatedCapacityMahAvg: Double?,
    val updatedAtMs: Long
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

data class SpeedTierThresholds(
    val normalMinWatts: Double = 7.5,
    val fastMinWatts: Double = 18.0,
    val veryFastMinWatts: Double = 35.0
)

data class MonitoringPreferences(
    val samplingIntervalSeconds: Int = 5,
    val foregroundServiceEnabled: Boolean = false,
    val alertsEnabled: Boolean = true,
    val highTempAlertCelsius: Float? = 42.0f,
    val thermalSlopeAlertCPerMin: Float? = 1.2f,
    val targetChargeAlertPercent: Int? = 80,
    val lowBatteryAlertPercent: Int? = 20,
    val userDesignCapacityMahOverride: Double? = null,
    val speedTierThresholds: SpeedTierThresholds = SpeedTierThresholds(),
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val useDynamicColor: Boolean = false,
    val hapticsEnabled: Boolean = true
)