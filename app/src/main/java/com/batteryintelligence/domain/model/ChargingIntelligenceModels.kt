package com.batteryintelligence.domain.model

data class ChargingEtaEstimate(
    val timeToTargetMs: Long?,
    val targetPercent: Int,
    val timeTo80Ms: Long?,
    val timeTo100Ms: Long?,
    val clockTimeAtTargetMs: Long?,
    val clockTimeAt100Ms: Long?,
    val confidence: ChargingConfidence,
    val chargeRatePercentPerMin: Double?,
    val isSlowTrickleClamped: Boolean = false
)

data class ChargingIntelligenceState(
    val snapshot: BatterySnapshot? = null,
    val smoothedPowerWatts: Double? = null,
    val smoothedCurrentMa: Double? = null,
    val speedTier: ChargingSpeedTier = ChargingSpeedTier.UNKNOWN,
    val chargingPhase: ChargingPhase = ChargingPhase.UNKNOWN,
    val etaEstimate: ChargingEtaEstimate? = null,
    val thermalSlopeCPerMin: Float? = null,
    val isThermalSlopeElevated: Boolean = false,
    val isThermalSlopeAlert: Boolean = false,
    val sessionSampleCount: Int = 0
)

data class ChargerSourceBenchmark(
    val chargingSource: ChargingSource,
    val speedTier: ChargingSpeedTier,
    val sessionCount: Int,
    val avgPowerWatts: Double?,
    val maxPowerWatts: Double?,
    val avgEfficiencyPercent: Double?,
    val avgTempRiseCelsius: Float?
)
