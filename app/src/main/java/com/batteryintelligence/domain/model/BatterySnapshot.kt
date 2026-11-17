package com.batteryintelligence.domain.model

import kotlin.math.abs

data class BatteryTelemetrySources(
    val cycleCountSource: MetricSource = MetricSource.UNAVAILABLE,
    val stateOfHealthSource: MetricSource = MetricSource.UNAVAILABLE,
    val designCapacitySource: MetricSource = MetricSource.UNAVAILABLE
)

data class BatterySnapshot(
    val timestampMs: Long,
    val levelPercent: Int?,
    val levelPrecisePercent: Float?,
    val chargingStatus: ChargingStatus,
    val chargingSource: ChargingSource,
    val healthCondition: BatteryHealthCondition,
    val isBatteryPresent: Boolean?,
    val isBatteryLow: Boolean?,
    val technology: String?,
    val temperatureCelsius: Float?,
    val voltageMv: Int?,
    val currentNowMa: Double?,
    val currentAverageMa: Double?,
    val chargeCounterUah: Long?,
    val energyCounterNwh: Long?,
    val cycleCount: Int?,
    val hardwareStateOfHealthPercent: Int?,
    val designCapacityMah: Double?,
    val chargeTimeRemainingMs: Long?,
    val telemetrySources: BatteryTelemetrySources = BatteryTelemetrySources()
) {
    val voltageVolts: Double?
        get() = voltageMv?.let { it / 1000.0 }

    val chargeCounterMah: Double?
        get() = chargeCounterUah?.let { it / 1000.0 }

    val powerWatts: Double?
        get() {
            val mv = voltageMv ?: return null
            val ma = currentNowMa ?: return null
            return (mv / 1000.0) * (abs(ma) / 1000.0)
        }

    val isCharging: Boolean
        get() = chargingStatus == ChargingStatus.CHARGING ||
            (chargingStatus == ChargingStatus.FULL && chargingSource.isPlugged)

    val chargingSpeedTier: ChargingSpeedTier
        get() {
            if (!chargingSource.isPlugged || chargingStatus == ChargingStatus.DISCHARGING) {
                return ChargingSpeedTier.DISCHARGING
            }
            val watts = powerWatts ?: return ChargingSpeedTier.UNKNOWN
            return when {
                watts < 5.0 -> ChargingSpeedTier.TRICKLE
                watts < 15.0 -> ChargingSpeedTier.STANDARD
                watts < 30.0 -> ChargingSpeedTier.FAST
                else -> ChargingSpeedTier.SUPER_FAST
            }
        }
}