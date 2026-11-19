package com.batteryintelligence.data.datasource

import kotlin.math.abs

object BatteryMetricNormalizer {

    /**
     * Normalizes OEM-dependent current readings (microamperes vs milliamperes) into signed mA.
     * Returns null if the hardware reports Int.MIN_VALUE, Long.MIN_VALUE, or 0.
     *
     * Handles the 10,001..30,000 ambiguity zone using an optional CHARGE_COUNTER-derived rate hint
     * so high-current dual-cell fast charging (>10 A in mA) is not misdivided by 1,000.
     */
    fun normalizeCurrentToMilliamps(
        rawValue: Long,
        chargeCounterRateMaHint: Double? = null
    ): Double? {
        if (rawValue == Long.MIN_VALUE ||
            rawValue == Int.MIN_VALUE.toLong() ||
            rawValue == 0L
        ) {
            return null
        }
        val absRaw = abs(rawValue)
        val normalized = when {
            absRaw > 30_000L -> rawValue / 1000.0
            absRaw > 10_000L -> {
                val asMa = rawValue.toDouble()
                val asUaInMa = rawValue / 1000.0
                if (chargeCounterRateMaHint != null &&
                    abs(abs(asMa) - abs(chargeCounterRateMaHint)) <
                    abs(abs(asUaInMa) - abs(chargeCounterRateMaHint)) &&
                    abs(chargeCounterRateMaHint) > 5_000.0
                ) {
                    asMa
                } else {
                    asUaInMa
                }
            }
            else -> rawValue.toDouble()
        }
        return normalized.takeIf { it.isFinite() && abs(it) <= 35_000.0 }
    }

    /**
     * Normalizes battery voltage into millivolts (handles occasional microvolt OEM reporting
     * and dual-cell series battery packs up to 25,000 mV).
     */
    fun normalizeVoltageToMillivolts(rawValue: Int): Int? {
        if (rawValue == Int.MIN_VALUE || rawValue <= 0) {
            return null
        }
        val normalized = when {
            rawValue > 100_000 -> rawValue / 1000
            rawValue in 1..25 -> rawValue * 1000
            else -> rawValue
        }
        return normalized.takeIf { it in 1_000..25_000 }
    }

    /**
     * Converts Android tenths-of-degree-Celsius into °C within physically plausible bounds,
     * rejecting isolated 0°C thermistor dropout glitches when a recent warm baseline exists.
     */
    fun normalizeTemperatureCelsius(
        rawTenths: Int,
        previousValidTempCelsius: Float? = null
    ): Float? {
        if (rawTenths == Int.MIN_VALUE || rawTenths !in -500..1500) {
            return null
        }
        val celsius = rawTenths / 10.0f
        if (rawTenths == 0 && previousValidTempCelsius != null && previousValidTempCelsius > 10.0f) {
            return null
        }
        return celsius
    }

    fun normalizeChargeCounterUah(rawValue: Long): Long? {
        if (rawValue == Long.MIN_VALUE ||
            rawValue == Int.MIN_VALUE.toLong() ||
            rawValue <= 0L
        ) {
            return null
        }
        return rawValue
    }

    fun normalizeEnergyCounterNwh(rawValue: Long): Long? {
        if (rawValue == Long.MIN_VALUE ||
            rawValue == Int.MIN_VALUE.toLong() ||
            rawValue <= 0L
        ) {
            return null
        }
        return rawValue
    }

    fun normalizeCycleCount(rawValue: Int): Int? {
        if (rawValue == Int.MIN_VALUE || rawValue <= 0) {
            return null
        }
        return rawValue
    }

    fun normalizeStateOfHealth(rawValue: Int): Int? {
        if (rawValue == Int.MIN_VALUE || rawValue !in 1..100) {
            return null
        }
        return rawValue
    }

    /**
     * Normalizes battery capacity values (uAh vs mAh) into mAh.
     */
    fun normalizeCapacityToMah(rawValue: Long): Double? {
        if (rawValue == Long.MIN_VALUE ||
            rawValue == Int.MIN_VALUE.toLong() ||
            rawValue <= 0L
        ) {
            return null
        }
        val mah = if (rawValue > 100_000L) rawValue / 1000.0 else rawValue.toDouble()
        return mah.takeIf { it.isFinite() && it > 100.0 }
    }

    /**
     * Evaluates signed current polarity relative to verified ChargingStatus.
     * Returns pair of (calibratedSignedCurrentMa, isNetDischargingWhilePlugged).
     */
    fun calibratePolarity(
        rawCurrentMa: Double?,
        chargingStatus: ChargingStatus,
        chargingPolarityPositive: Boolean?
    ): Pair<Double?, Boolean> {
        if (rawCurrentMa == null) return null to false
        if (chargingPolarityPositive == null) {
            return rawCurrentMa to false
        }
        val isChargingDirection = if (chargingPolarityPositive) {
            rawCurrentMa > 0.0
        } else {
            rawCurrentMa < 0.0
        }
        val isNetDrainWhileCharging = chargingStatus == ChargingStatus.CHARGING && !isChargingDirection
        val canonicalSigned = if (isChargingDirection) abs(rawCurrentMa) else -abs(rawCurrentMa)
        return canonicalSigned to isNetDrainWhileCharging
    }
}