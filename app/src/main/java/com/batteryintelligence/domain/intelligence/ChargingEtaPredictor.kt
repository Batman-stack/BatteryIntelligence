package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingConfidence
import com.batteryintelligence.domain.model.ChargingEtaEstimate
import com.batteryintelligence.domain.model.ChargingPhase
import com.batteryintelligence.domain.model.ChargingStatus
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.math.sqrt

@Singleton
class ChargingEtaPredictor @Inject constructor() {

    private data class EtaSample(
        val timestampMs: Long,
        val levelPrecisePercent: Float,
        val currentMa: Double?,
        val powerWatts: Double?,
        val chargeCounterUah: Long?
    )

    private val samples = ArrayDeque<EtaSample>(MAX_WINDOW_SAMPLES)
    private var sessionStartTimeMs: Long = 0L
    private var sessionStartLevel: Float? = null
    private var peakObservedCurrentMa: Double = 0.0
    private var ccCvKneePercent: Float = DEFAULT_TAPER_KNEE_PERCENT

    @Synchronized
    fun reset() {
        samples.clear()
        sessionStartTimeMs = 0L
        sessionStartLevel = null
        peakObservedCurrentMa = 0.0
        ccCvKneePercent = DEFAULT_TAPER_KNEE_PERCENT
    }

    @Synchronized
    fun update(
        snapshot: BatterySnapshot,
        smoothedCurrentMa: Double?,
        effectiveCapacityMah: Double?,
        targetPercent: Int
    ): Pair<ChargingEtaEstimate?, ChargingPhase> {
        val safeTarget = targetPercent.coerceIn(50, 100)

        if (!snapshot.chargingSource.isPlugged || snapshot.chargingStatus == ChargingStatus.DISCHARGING) {
            reset()
            return null to ChargingPhase.DISCHARGING
        }
        if (snapshot.chargingStatus == ChargingStatus.FULL || (snapshot.levelPercent ?: 0) >= 100) {
            return ChargingEtaEstimate(
                timeToTargetMs = 0L,
                targetPercent = safeTarget,
                timeTo80Ms = 0L,
                timeTo100Ms = 0L,
                clockTimeAtTargetMs = snapshot.timestampMs,
                clockTimeAt100Ms = snapshot.timestampMs,
                confidence = ChargingConfidence.HIGH,
                chargeRatePercentPerMin = 0.0
            ) to ChargingPhase.FULL
        }
        if (snapshot.chargingStatus == ChargingStatus.NOT_CHARGING) {
            return ChargingEtaEstimate(
                timeToTargetMs = null,
                targetPercent = safeTarget,
                timeTo80Ms = null,
                timeTo100Ms = null,
                clockTimeAtTargetMs = null,
                clockTimeAt100Ms = null,
                confidence = ChargingConfidence.CALCULATING,
                chargeRatePercentPerMin = null
            ) to ChargingPhase.PAUSED
        }

        val currentLevel = snapshot.levelPrecisePercent ?: snapshot.levelPercent?.toFloat()
            ?: return null to ChargingPhase.UNKNOWN

        if (sessionStartTimeMs == 0L) {
            sessionStartTimeMs = snapshot.timestampMs
            sessionStartLevel = currentLevel
        }

        val absCurrentMa = (smoothedCurrentMa ?: snapshot.effectiveCurrentMa)?.let { abs(it) }
        if (absCurrentMa != null && absCurrentMa > peakObservedCurrentMa) {
            peakObservedCurrentMa = absCurrentMa
        }

        if (samples.size >= MAX_WINDOW_SAMPLES) {
            samples.removeFirst()
        }
        samples.addLast(
            EtaSample(
                timestampMs = snapshot.timestampMs,
                levelPrecisePercent = currentLevel,
                currentMa = absCurrentMa,
                powerWatts = snapshot.powerWatts,
                chargeCounterUah = snapshot.chargeCounterUah
            )
        )

        val phase = detectChargingPhase(currentLevel, absCurrentMa)

        val elapsedMs = (snapshot.timestampMs - sessionStartTimeMs).coerceAtLeast(0L)
        if (elapsedMs < MIN_STABILIZATION_MS || samples.size < MIN_SAMPLES_FOR_ETA) {
            return ChargingEtaEstimate(
                timeToTargetMs = null,
                targetPercent = safeTarget,
                timeTo80Ms = null,
                timeTo100Ms = null,
                clockTimeAtTargetMs = null,
                clockTimeAt100Ms = null,
                confidence = ChargingConfidence.CALCULATING,
                chargeRatePercentPerMin = null
            ) to phase
        }

        val coulombRatePctPerMin = computeCoulombRatePctPerMin(
            absCurrentMa = absCurrentMa,
            effectiveCapacityMah = effectiveCapacityMah
        )
        val empiricalRatePctPerMin = computeEmpiricalRatePctPerMin()

        val baseRatePctPerMin = when {
            coulombRatePctPerMin != null && empiricalRatePctPerMin != null -> {
                val ratioDiff = abs(coulombRatePctPerMin - empiricalRatePctPerMin) /
                    maxOf(coulombRatePctPerMin, empiricalRatePctPerMin, 0.01)
                if (ratioDiff <= 0.40) {
                    0.65 * coulombRatePctPerMin + 0.35 * empiricalRatePctPerMin
                } else {
                    0.50 * coulombRatePctPerMin + 0.50 * empiricalRatePctPerMin
                }
            }
            coulombRatePctPerMin != null -> coulombRatePctPerMin
            empiricalRatePctPerMin != null -> empiricalRatePctPerMin
            else -> null
        }

        if (baseRatePctPerMin == null || baseRatePctPerMin <= MIN_VALID_RATE_PCT_PER_MIN) {
            val isTrickle = baseRatePctPerMin != null && baseRatePctPerMin > 0.0
            return ChargingEtaEstimate(
                timeToTargetMs = null,
                targetPercent = safeTarget,
                timeTo80Ms = null,
                timeTo100Ms = null,
                clockTimeAtTargetMs = null,
                clockTimeAt100Ms = null,
                confidence = if (isTrickle) ChargingConfidence.LOW else ChargingConfidence.CALCULATING,
                chargeRatePercentPerMin = baseRatePctPerMin,
                isSlowTrickleClamped = isTrickle
            ) to phase
        }

        val rawTimeTo100Ms = integrateRemainingTimeMs(
            currentSoc = currentLevel.toDouble(),
            targetSoc = 100.0,
            baseRatePctPerMin = baseRatePctPerMin
        )
        val rawTimeToTargetMs = if (currentLevel >= safeTarget) {
            0L
        } else {
            integrateRemainingTimeMs(
                currentSoc = currentLevel.toDouble(),
                targetSoc = safeTarget.toDouble(),
                baseRatePctPerMin = baseRatePctPerMin
            )
        }
        val rawTimeTo80Ms = if (currentLevel >= 80f) {
            0L
        } else {
            integrateRemainingTimeMs(
                currentSoc = currentLevel.toDouble(),
                targetSoc = 80.0,
                baseRatePctPerMin = baseRatePctPerMin
            )
        }

        val clamped100Ms = clampEtaMs(rawTimeTo100Ms)
        val clampedTargetMs = if (rawTimeToTargetMs == 0L) 0L else clampEtaMs(rawTimeToTargetMs)
        val clamped80Ms = if (rawTimeTo80Ms == 0L) 0L else clampEtaMs(rawTimeTo80Ms)
        val isTrickleClamped = rawTimeTo100Ms != null && rawTimeTo100Ms > MAX_ETA_MS

        val confidence = scoreConfidence(
            elapsedMs = elapsedMs,
            currentSoc = currentLevel,
            coulombRate = coulombRatePctPerMin,
            empiricalRate = empiricalRatePctPerMin,
            effectiveCapacityMah = effectiveCapacityMah,
            hasCurrentSensor = absCurrentMa != null
        )

        return ChargingEtaEstimate(
            timeToTargetMs = clampedTargetMs,
            targetPercent = safeTarget,
            timeTo80Ms = clamped80Ms,
            timeTo100Ms = clamped100Ms,
            clockTimeAtTargetMs = clampedTargetMs?.let { snapshot.timestampMs + it },
            clockTimeAt100Ms = clamped100Ms?.let { snapshot.timestampMs + it },
            confidence = confidence,
            chargeRatePercentPerMin = baseRatePctPerMin,
            isSlowTrickleClamped = isTrickleClamped
        ) to phase
    }

    private fun detectChargingPhase(currentSoc: Float, absCurrentMa: Double?): ChargingPhase {
        if (currentSoc >= 95f) return ChargingPhase.TAPER
        if (currentSoc >= ccCvKneePercent) {
            if (peakObservedCurrentMa > 400.0 && absCurrentMa != null && absCurrentMa < peakObservedCurrentMa * 0.72) {
                return ChargingPhase.CV
            }
            if (currentSoc >= 85f) return ChargingPhase.CV
        }
        if (currentSoc >= 80f && peakObservedCurrentMa > 400.0 && absCurrentMa != null &&
            absCurrentMa < peakObservedCurrentMa * 0.68
        ) {
            ccCvKneePercent = currentSoc.coerceIn(80f, 85f)
            return ChargingPhase.CV
        }
        return ChargingPhase.CC
    }

    private fun computeCoulombRatePctPerMin(
        absCurrentMa: Double?,
        effectiveCapacityMah: Double?
    ): Double? {
        if (absCurrentMa == null || effectiveCapacityMah == null || effectiveCapacityMah <= 100.0) {
            return null
        }
        val rate = (absCurrentMa / effectiveCapacityMah) * (100.0 / 60.0)
        return rate.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun computeEmpiricalRatePctPerMin(): Double? {
        if (samples.size < 2) return null
        val first = samples.first()
        val last = samples.last()
        val dtMin = (last.timestampMs - first.timestampMs) / 60_000.0
        val dSoc = (last.levelPrecisePercent - first.levelPrecisePercent).toDouble()
        if (dtMin < 1.5 || dSoc < 1.0) return null
        val rate = dSoc / dtMin
        return rate.takeIf { it.isFinite() && it > 0.0 }
    }

    /**
     * Piecewise CC -> CV taper integration across 5% SoC buckets above the taper knee (80-85%).
     */
    private fun integrateRemainingTimeMs(
        currentSoc: Double,
        targetSoc: Double,
        baseRatePctPerMin: Double
    ): Long? {
        if (targetSoc <= currentSoc) return 0L
        if (baseRatePctPerMin <= 0.0) return null

        val knee = ccCvKneePercent.toDouble()
        val currentBucketMultiplier = taperMultiplierForSoc(currentSoc, knee)

        var totalMinutes = 0.0
        var cursor = currentSoc
        val step = 1.0

        while (cursor < targetSoc) {
            val next = minOf(cursor + step, targetSoc)
            val mid = (cursor + next) / 2.0
            val deltaSoc = next - cursor
            val targetMultiplier = taperMultiplierForSoc(mid, knee)
            // If we are already inside CV, baseRatePctPerMin is already slowed by currentBucketMultiplier
            val relativeSlowdown = (targetMultiplier / currentBucketMultiplier).coerceAtLeast(1.0)
            totalMinutes += (deltaSoc / baseRatePctPerMin) * relativeSlowdown
            cursor = next
        }

        val ms = (totalMinutes * 60_000.0).roundToLong()
        return ms.takeIf { it >= 0L }
    }

    private fun taperMultiplierForSoc(soc: Double, knee: Double): Double {
        return when {
            soc < knee -> 1.0
            soc < 85.0 -> 1.35
            soc < 90.0 -> 1.75
            soc < 95.0 -> 2.40
            else -> 3.50
        }
    }

    private fun clampEtaMs(rawMs: Long?): Long? {
        if (rawMs == null) return null
        if (rawMs > MAX_ETA_MS) return null
        return rawMs.coerceAtLeast(MIN_DISPLAY_ETA_MS)
    }

    private fun scoreConfidence(
        elapsedMs: Long,
        currentSoc: Float,
        coulombRate: Double?,
        empiricalRate: Double?,
        effectiveCapacityMah: Double?,
        hasCurrentSensor: Boolean
    ): ChargingConfidence {
        val powerValues = samples.mapNotNull { it.powerWatts }
        val cv = computeCoefficientOfVariation(powerValues)

        if (!hasCurrentSensor || currentSoc >= 93f || (cv != null && cv > 0.35)) {
            return ChargingConfidence.LOW
        }

        val pathsAgree = if (coulombRate != null && empiricalRate != null) {
            val diff = abs(coulombRate - empiricalRate) / maxOf(coulombRate, empiricalRate, 0.01)
            diff <= 0.22
        } else {
            false
        }

        return if (elapsedMs >= 180_000L && pathsAgree && effectiveCapacityMah != null && (cv == null || cv < 0.15)) {
            ChargingConfidence.HIGH
        } else {
            ChargingConfidence.MEDIUM
        }
    }

    private fun computeCoefficientOfVariation(values: List<Double>): Double? {
        if (values.size < 4) return null
        val mean = values.average()
        if (mean <= 0.2) return null
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance) / mean
    }

    companion object {
        const val MAX_WINDOW_SAMPLES = 45
        const val MIN_STABILIZATION_MS = 45_000L
        const val MIN_SAMPLES_FOR_ETA = 3
        const val DEFAULT_TAPER_KNEE_PERCENT = 80f
        const val MIN_VALID_RATE_PCT_PER_MIN = 0.02
        const val MIN_DISPLAY_ETA_MS = 60_000L
        const val MAX_ETA_MS = 24L * 3_600_000L
    }
}
