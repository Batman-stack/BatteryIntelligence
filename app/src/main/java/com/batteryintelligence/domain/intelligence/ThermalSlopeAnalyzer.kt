package com.batteryintelligence.domain.intelligence

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThermalSlopeAnalyzer @Inject constructor() {

    private data class TempPoint(
        val timestampMs: Long,
        val tempCelsius: Float
    )

    private val points = ArrayDeque<TempPoint>(MAX_POINTS)

    @Synchronized
    fun reset() {
        points.clear()
    }

    /**
     * Computes the rolling battery temperature rate of change in °C/min over the last 2–5 minutes.
     * Returns null until at least 60 seconds of temperature samples are accumulated.
     */
    @Synchronized
    fun update(timestampMs: Long, temperatureCelsius: Float?): Float? {
        if (temperatureCelsius == null || !temperatureCelsius.isFinite()) {
            return null
        }

        if (points.isNotEmpty() && timestampMs - points.last().timestampMs > MAX_GAP_RESET_MS) {
            points.clear()
        }

        points.addLast(TempPoint(timestampMs, temperatureCelsius))
        while (points.size > MAX_POINTS || (points.size > 2 && timestampMs - points.first().timestampMs > WINDOW_MS)) {
            points.removeFirst()
        }

        if (points.size < 2) return null
        val spanMs = points.last().timestampMs - points.first().timestampMs
        if (spanMs < MIN_SPAN_MS) return null

        // Least-squares linear regression slope for noise immunity against 0.1°C sensor quantization
        val t0 = points.first().timestampMs
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumXX = 0.0
        val n = points.size.toDouble()

        for (pt in points) {
            val xMin = (pt.timestampMs - t0) / 60_000.0
            val y = pt.tempCelsius.toDouble()
            sumX += xMin
            sumY += y
            sumXY += xMin * y
            sumXX += xMin * xMin
        }

        val denom = n * sumXX - sumX * sumX
        if (denom <= 1e-6) return null
        val slopeCPerMin = ((n * sumXY - sumX * sumY) / denom).toFloat()
        return slopeCPerMin.takeIf { it.isFinite() && it in -10.0f..10.0f }
    }

    companion object {
        private const val MAX_POINTS = 60
        private const val WINDOW_MS = 180_000L
        private const val MIN_SPAN_MS = 60_000L
        private const val MAX_GAP_RESET_MS = 10L * 60_000L
    }
}
