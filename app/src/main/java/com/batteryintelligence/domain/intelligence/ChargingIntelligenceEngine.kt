package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.MonitoringPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChargingIntelligenceEngine @Inject constructor(
    private val speedClassifier: ChargingSpeedClassifier,
    private val etaPredictor: ChargingEtaPredictor,
    private val thermalSlopeAnalyzer: ThermalSlopeAnalyzer
) {

    private val _state = MutableStateFlow(ChargingIntelligenceState())
    val state: StateFlow<ChargingIntelligenceState> = _state.asStateFlow()

    @Volatile
    private var lastProcessedTimestampMs: Long = 0L

    @Synchronized
    fun processSnapshot(
        snapshot: BatterySnapshot,
        preferences: MonitoringPreferences,
        effectiveCapacityMah: Double?
    ): ChargingIntelligenceState {
        // Deduplicate identical-timestamp emissions when FGS and UI both observe telemetry
        if (snapshot.timestampMs <= lastProcessedTimestampMs && _state.value.snapshot != null) {
            return _state.value
        }
        lastProcessedTimestampMs = snapshot.timestampMs

        val speedResult = speedClassifier.update(
            snapshot = snapshot,
            thresholds = preferences.speedTierThresholds
        )

        val slopeCPerMin = thermalSlopeAnalyzer.update(
            timestampMs = snapshot.timestampMs,
            temperatureCelsius = snapshot.temperatureCelsius
        )

        val targetPercent = (preferences.targetChargeAlertPercent ?: 80).coerceIn(50, 100)
        val capacityForEta = preferences.userDesignCapacityMahOverride
            ?.takeIf { it > 0.0 }
            ?: effectiveCapacityMah
            ?: snapshot.designCapacityMah

        val (etaEstimate, phase) = etaPredictor.update(
            snapshot = snapshot,
            smoothedCurrentMa = speedResult.smoothedCurrentMa,
            effectiveCapacityMah = capacityForEta,
            targetPercent = targetPercent,
            thermalSlopeCPerMin = slopeCPerMin
        )

        val slopeAlertLimit = preferences.thermalSlopeAlertCPerMin ?: 1.2f
        val isSlopeElevated = slopeCPerMin != null && slopeCPerMin >= (slopeAlertLimit * 0.65f)
        val isSlopeAlert = slopeCPerMin != null && slopeCPerMin >= slopeAlertLimit

        val prevCount = if (snapshot.isCharging) _state.value.sessionSampleCount + 1 else 0
        val isStallOrTrickle = snapshot.isCharging &&
            (snapshot.levelPercent ?: 0) < 95 &&
            (snapshot.isNetBatteryDischargingWhilePlugged ||
                etaEstimate?.isSlowTrickleClamped == true ||
                (speedResult.smoothedPowerWatts != null && speedResult.smoothedPowerWatts < 2.5 && prevCount >= 5))

        val next = ChargingIntelligenceState(
            snapshot = snapshot,
            smoothedPowerWatts = speedResult.smoothedPowerWatts,
            smoothedCurrentMa = speedResult.smoothedCurrentMa,
            speedTier = speedResult.speedTier,
            chargingPhase = phase,
            etaEstimate = etaEstimate,
            thermalSlopeCPerMin = slopeCPerMin,
            isThermalSlopeElevated = isSlopeElevated,
            isThermalSlopeAlert = isSlopeAlert,
            isStallOrTrickleDetected = isStallOrTrickle,
            sessionSampleCount = prevCount
        )
        _state.value = next
        return next
    }

    @Synchronized
    fun resetSession() {
        speedClassifier.reset()
        etaPredictor.reset()
        lastProcessedTimestampMs = 0L
        _state.value = _state.value.copy(
            etaEstimate = null,
            sessionSampleCount = 0
        )
    }
}
