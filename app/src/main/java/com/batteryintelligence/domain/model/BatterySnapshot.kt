package com.batteryintelligence.domain.model

import kotlin.math.abs

data class BatteryTelemetrySources(
    val cycleCountSource: MetricSource = MetricSource.UNAVAILABLE,
    val stateOfHealthSource: MetricSource = MetricSource.UNAVAILABLE,
    val designCapacitySource: MetricSource = MetricSource.UNAVAILABLE,
    val currentSource: MetricSource = MetricSource.UNAVAILABLE
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
    val signedCurrentMa: Double? = currentNowMa,
    val isNetBatteryDischargingWhilePlugged: Boolean = false,
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

    val effectiveCurrentMa: Double?
        get() = currentNowMa ?: currentAverageMa

    /**
     * Canonical battery-side electrical power formula:
     * Power (W) = Voltage (mV) * |Current (mA)| / 1,000,000
     * Returns null when either voltage or current sensor is unavailable, or when plugged in
     * but net battery flow is discharging into device load.
     */
    val powerWatts: Double?
        get() {
            val mv = voltageMv ?: return null
            val ma = effectiveCurrentMa ?: return null
            if (isNetBatteryDischargingWhilePlugged) return 0.0
            return (mv.toDouble() * abs(ma)) / 1_000_000.0
        }

    val isCharging: Boolean
        get() = chargingStatus == ChargingStatus.CHARGING ||
            (chargingStatus == ChargingStatus.FULL && chargingSource.isPlugged)

    val chargingSpeedTier: ChargingSpeedTier
        get() {
            if (!chargingSource.isPlugged || chargingStatus == ChargingStatus.DISCHARGING) {
                return ChargingSpeedTier.DISCHARGING
            }
            if (chargingStatus == ChargingStatus.NOT_CHARGING) {
                return ChargingSpeedTier.PAUSED_HOLD
            }
            val watts = powerWatts ?: return ChargingSpeedTier.UNKNOWN
            return when {
                watts < 7.5 -> ChargingSpeedTier.SLOW
                watts < 18.0 -> ChargingSpeedTier.NORMAL
                watts < 35.0 -> ChargingSpeedTier.FAST
                else -> ChargingSpeedTier.VERY_FAST
            }
        }
}