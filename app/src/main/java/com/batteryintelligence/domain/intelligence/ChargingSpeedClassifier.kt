package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.ChargingStatus
import com.batteryintelligence.domain.model.SpeedTierThresholds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

data class SpeedClassificationResult(
    val smoothedPowerWatts: Double?,
    val smoothedCurrentMa: Double?,
    val speedTier: ChargingSpeedTier
)

@Singleton
class ChargingSpeedClassifier @Inject constructor() {

    private val powerWindow = ArrayDeque<Double>(MEDIAN_WINDOW_SIZE)
    private var emaPowerWatts: Double? = null
    private var emaCurrentMa: Double? = null
    private var lastSampleTimestampMs: Long = 0L

    private var activeTier: ChargingSpeedTier = ChargingSpeedTier.UNKNOWN
    private var candidateTier: ChargingSpeedTier? = null
    private var candidateSinceMs: Long = 0L
    private var validChargingSampleCount: Int = 0

    @Synchronized
    fun reset() {
        powerWindow.clear()
        emaPowerWatts = null
        emaCurrentMa = null
        lastSampleTimestampMs = 0L
        activeTier = ChargingSpeedTier.UNKNOWN
        candidateTier = null
        candidateSinceMs = 0L
        validChargingSampleCount = 0
    }

    @Synchronized
    fun update(
        snapshot: BatterySnapshot,
        thresholds: SpeedTierThresholds = SpeedTierThresholds()
    ): SpeedClassificationResult {
        if (!snapshot.chargingSource.isPlugged || snapshot.chargingStatus == ChargingStatus.DISCHARGING) {
            reset()
            return SpeedClassificationResult(
                smoothedPowerWatts = snapshot.powerWatts,
                smoothedCurrentMa = snapshot.effectiveCurrentMa,
                speedTier = ChargingSpeedTier.DISCHARGING
            )
        }

        if (snapshot.chargingStatus == ChargingStatus.FULL) {
            candidateTier = null
            activeTier = ChargingSpeedTier.SLOW
            return SpeedClassificationResult(
                smoothedPowerWatts = emaPowerWatts ?: snapshot.powerWatts,
                smoothedCurrentMa = emaCurrentMa ?: snapshot.effectiveCurrentMa,
                speedTier = ChargingSpeedTier.SLOW
            )
        }

        if (snapshot.chargingStatus == ChargingStatus.NOT_CHARGING) {
            candidateTier = null
            activeTier = ChargingSpeedTier.PAUSED_HOLD
            return SpeedClassificationResult(
                smoothedPowerWatts = 0.0,
                smoothedCurrentMa = 0.0,
                speedTier = ChargingSpeedTier.PAUSED_HOLD
            )
        }

        val rawPower = snapshot.powerWatts
        if (rawPower == null) {
            val tier = if (activeTier == ChargingSpeedTier.UNKNOWN) {
                ChargingSpeedTier.CALCULATING
            } else {
                activeTier
            }
            return SpeedClassificationResult(
                smoothedPowerWatts = emaPowerWatts,
                smoothedCurrentMa = emaCurrentMa,
                speedTier = tier
            )
        }

        validChargingSampleCount++
        if (powerWindow.size >= MEDIAN_WINDOW_SIZE) {
            powerWindow.removeFirst()
        }
        powerWindow.addLast(rawPower)

        val medianPower = computeMedian(powerWindow)
        val dtSec = if (lastSampleTimestampMs > 0L && snapshot.timestampMs > lastSampleTimestampMs) {
            ((snapshot.timestampMs - lastSampleTimestampMs) / 1000.0).coerceIn(0.5, 60.0)
        } else {
            3.0
        }
        lastSampleTimestampMs = snapshot.timestampMs

        val alpha = (1.0 - exp(-dtSec / EMA_TIME_CONSTANT_SEC)).coerceIn(0.15, 0.65)
        val prevEmaPower = emaPowerWatts
        val nextEmaPower = if (prevEmaPower == null) medianPower else (alpha * medianPower + (1.0 - alpha) * prevEmaPower)
        emaPowerWatts = nextEmaPower

        val rawCurr = snapshot.effectiveCurrentMa?.let { kotlin.math.abs(it) }
        if (rawCurr != null) {
            val prevCurr = emaCurrentMa
            emaCurrentMa = if (prevCurr == null) rawCurr else (alpha * rawCurr + (1.0 - alpha) * prevCurr)
        }

        if (validChargingSampleCount < MIN_SAMPLES_BEFORE_CLASSIFY && activeTier == ChargingSpeedTier.UNKNOWN) {
            return SpeedClassificationResult(
                smoothedPowerWatts = nextEmaPower,
                smoothedCurrentMa = emaCurrentMa,
                speedTier = ChargingSpeedTier.CALCULATING
            )
        }

        if (activeTier == ChargingSpeedTier.UNKNOWN || activeTier == ChargingSpeedTier.CALCULATING ||
            activeTier == ChargingSpeedTier.DISCHARGING || activeTier == ChargingSpeedTier.PAUSED_HOLD
        ) {
            activeTier = rawTierForPower(nextEmaPower, thresholds)
            candidateTier = null
            candidateSinceMs = snapshot.timestampMs
            return SpeedClassificationResult(
                smoothedPowerWatts = nextEmaPower,
                smoothedCurrentMa = emaCurrentMa,
                speedTier = activeTier
            )
        }

        val desiredTier = desiredTierWithHysteresis(
            currentTier = activeTier,
            smoothedWatts = nextEmaPower,
            thresholds = thresholds
        )

        if (desiredTier == activeTier) {
            candidateTier = null
            candidateSinceMs = 0L
        } else {
            if (candidateTier != desiredTier) {
                candidateTier = desiredTier
                candidateSinceMs = snapshot.timestampMs
            } else if (snapshot.timestampMs - candidateSinceMs >= DWELL_TIME_MS) {
                activeTier = desiredTier
                candidateTier = null
                candidateSinceMs = 0L
            }
        }

        return SpeedClassificationResult(
            smoothedPowerWatts = nextEmaPower,
            smoothedCurrentMa = emaCurrentMa,
            speedTier = activeTier
        )
    }

    private fun rawTierForPower(
        watts: Double,
        thresholds: SpeedTierThresholds
    ): ChargingSpeedTier {
        return when {
            watts < thresholds.normalMinWatts -> ChargingSpeedTier.SLOW
            watts < thresholds.fastMinWatts -> ChargingSpeedTier.NORMAL
            watts < thresholds.veryFastMinWatts -> ChargingSpeedTier.FAST
            else -> ChargingSpeedTier.VERY_FAST
        }
    }

    private fun desiredTierWithHysteresis(
        currentTier: ChargingSpeedTier,
        smoothedWatts: Double,
        thresholds: SpeedTierThresholds
    ): ChargingSpeedTier {
        val slowToNormalDeadband = 1.0
        val normalToFastDeadband = 1.5
        val fastToVeryFastDeadband = 2.0

        return when (currentTier) {
            ChargingSpeedTier.SLOW -> when {
                smoothedWatts >= thresholds.veryFastMinWatts + fastToVeryFastDeadband -> ChargingSpeedTier.VERY_FAST
                smoothedWatts >= thresholds.fastMinWatts + normalToFastDeadband -> ChargingSpeedTier.FAST
                smoothedWatts >= thresholds.normalMinWatts + slowToNormalDeadband -> ChargingSpeedTier.NORMAL
                else -> ChargingSpeedTier.SLOW
            }
            ChargingSpeedTier.NORMAL -> when {
                smoothedWatts < thresholds.normalMinWatts - slowToNormalDeadband -> ChargingSpeedTier.SLOW
                smoothedWatts >= thresholds.veryFastMinWatts + fastToVeryFastDeadband -> ChargingSpeedTier.VERY_FAST
                smoothedWatts >= thresholds.fastMinWatts + normalToFastDeadband -> ChargingSpeedTier.FAST
                else -> ChargingSpeedTier.NORMAL
            }
            ChargingSpeedTier.FAST -> when {
                smoothedWatts < thresholds.normalMinWatts - slowToNormalDeadband -> ChargingSpeedTier.SLOW
                smoothedWatts < thresholds.fastMinWatts - normalToFastDeadband -> ChargingSpeedTier.NORMAL
                smoothedWatts >= thresholds.veryFastMinWatts + fastToVeryFastDeadband -> ChargingSpeedTier.VERY_FAST
                else -> ChargingSpeedTier.FAST
            }
            ChargingSpeedTier.VERY_FAST -> when {
                smoothedWatts < thresholds.normalMinWatts - slowToNormalDeadband -> ChargingSpeedTier.SLOW
                smoothedWatts < thresholds.fastMinWatts - normalToFastDeadband -> ChargingSpeedTier.NORMAL
                smoothedWatts < thresholds.veryFastMinWatts - fastToVeryFastDeadband -> ChargingSpeedTier.FAST
                else -> ChargingSpeedTier.VERY_FAST
            }
            else -> rawTierForPower(smoothedWatts, thresholds)
        }
    }

    private fun computeMedian(values: ArrayDeque<Double>): Double {
        if (values.size == 1) return values.first()
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    companion object {
        const val MEDIAN_WINDOW_SIZE = 5
        const val MIN_SAMPLES_BEFORE_CLASSIFY = 3
        const val DWELL_TIME_MS = 12_000L
        const val EMA_TIME_CONSTANT_SEC = 10.0
    }
}
