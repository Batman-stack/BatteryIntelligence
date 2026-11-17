package com.batteryintelligence.data.datasource

import kotlin.math.abs

object BatteryMetricNormalizer {

    /**
     * Normalizes OEM-dependent current readings (microamperes vs milliamperes) into mA.
     * Returns null if the hardware reports Int.MIN_VALUE, Long.MIN_VALUE, or 0.
     */
    fun normalizeCurrentToMilliamps(rawValue: Long): Double? {
        if (rawValue == Long.MIN_VALUE ||
            rawValue == Int.MIN_VALUE.toLong() ||
            rawValue == 0L
        ) {
            return null
        }
        return if (abs(rawValue) > 10_000L) {
            rawValue / 1000.0
        } else {
            rawValue.toDouble()
        }
    }

    /**
     * Normalizes battery voltage into millivolts (handles occasional microvolt OEM reporting).
     */
    fun normalizeVoltageToMillivolts(rawValue: Int): Int? {
        if (rawValue == Int.MIN_VALUE || rawValue <= 0) {
            return null
        }
        val normalized = if (rawValue > 100_000) rawValue / 1000 else rawValue
        return normalized.takeIf { it in 1_000..25_000 }
    }

    /**
     * Converts Android tenths-of-degree-Celsius into °C within physically plausible bounds.
     */
    fun normalizeTemperatureCelsius(rawTenths: Int): Float? {
        if (rawTenths == Int.MIN_VALUE || rawTenths !in -500..1500) {
            return null
        }
        return rawTenths / 10.0f
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
}