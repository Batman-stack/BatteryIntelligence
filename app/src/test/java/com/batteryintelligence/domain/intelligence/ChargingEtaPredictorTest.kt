package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.BatteryHealthCondition
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingConfidence
import com.batteryintelligence.domain.model.ChargingEtaEstimate
import com.batteryintelligence.domain.model.ChargingPhase
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChargingEtaPredictorTest {

    private lateinit var predictor: ChargingEtaPredictor

    @Before
    fun setUp() {
        predictor = ChargingEtaPredictor()
    }

    @Test
    fun `initial samples under 45s suppress ETA and report CALCULATING confidence`() {
        val (eta1, phase1) = predictor.update(
            snapshot = snapshotAt(0L, 40f, 3000.0),
            smoothedCurrentMa = 3000.0,
            effectiveCapacityMah = 5000.0,
            targetPercent = 80
        )
        assertNull(eta1?.timeTo100Ms)
        assertEquals(ChargingConfidence.CALCULATING, eta1?.confidence)
        assertEquals(ChargingPhase.CC, phase1)

        val (eta2, _) = predictor.update(
            snapshot = snapshotAt(30_000L, 40.5f, 3000.0),
            smoothedCurrentMa = 3000.0,
            effectiveCapacityMah = 5000.0,
            targetPercent = 80
        )
        assertNull(eta2?.timeTo100Ms)
        assertEquals(ChargingConfidence.CALCULATING, eta2?.confidence)
    }

    @Test
    fun `hybrid predictor incorporates CC-to-CV taper above 80 percent`() {
        // 5000mAh battery at 3000mA = 1.0 %/min linear rate
        // Remaining from 50% to 100% linear would be 50 min, with CV taper should be substantially > 50 min
        var timeMs = 0L
        var soc = 50.0f
        var latestEstimate = predictor.update(
            snapshotAt(timeMs, soc, 3000.0),
            smoothedCurrentMa = 3000.0,
            effectiveCapacityMah = 5000.0,
            targetPercent = 80
        ).first

        repeat(6) {
            timeMs += 30_000L
            soc += 0.5f // empirical 1.0%/min matches coulomb 1.0%/min
            latestEstimate = predictor.update(
                snapshotAt(timeMs, soc, 3000.0),
                smoothedCurrentMa = 3000.0,
                effectiveCapacityMah = 5000.0,
                targetPercent = 80
            ).first
        }

        assertNotNull(latestEstimate?.timeTo100Ms)
        assertNotNull(latestEstimate?.timeTo80Ms)
        assertEquals(ChargingConfidence.HIGH, latestEstimate?.confidence)

        val to80Min = latestEstimate!!.timeTo80Ms!! / 60_000.0
        val to100Min = latestEstimate.timeTo100Ms!! / 60_000.0
        // Remaining 27% to 80% in CC should be ~27 minutes
        assertTrue("Expected timeTo80 ~27m, got $to80Min", to80Min in 24.0..30.0)
        // Last 20% (80->100) has taper multipliers (1.35, 1.75, 2.4, 3.5 -> avg ~2.25x -> ~45m instead of 20m)
        assertTrue("Expected CV taper to push timeTo100 > 65m, got $to100Min", to100Min > 65.0)
    }

    @Test
    fun `already past custom target percent reports 0ms for timeToTarget while estimating 100`() {
        predictor.update(snapshotAt(0L, 72f, 2500.0), 2500.0, 5000.0, targetPercent = 70)
        predictor.update(snapshotAt(30_000L, 72.4f, 2500.0), 2500.0, 5000.0, targetPercent = 70)
        val (eta, _) = predictor.update(snapshotAt(60_000L, 72.8f, 2500.0), 2500.0, 5000.0, targetPercent = 70)

        assertEquals(0L, eta?.timeToTargetMs)
        assertTrue((eta?.timeTo100Ms ?: 0L) > 0L)
    }

    @Test
    fun `missing current sensor downgrades confidence to LOW once empirical rate exists`() {
        predictor.update(snapshotAt(0L, 40f, null), null, null, targetPercent = 80)
        predictor.update(snapshotAt(60_000L, 41f, null), null, null, targetPercent = 80)
        val (eta, _) = predictor.update(snapshotAt(120_000L, 42f, null), null, null, targetPercent = 80)

        assertNotNull(eta?.timeTo100Ms)
        assertEquals(ChargingConfidence.LOW, eta?.confidence)
    }

    @Test
    fun `drop in current above 80 percent detects CV charging phase`() {
        predictor.update(snapshotAt(0L, 60f, 4000.0), 4000.0, 5000.0, targetPercent = 85)
        val (_, cvPhase) = predictor.update(snapshotAt(60_000L, 82f, 1800.0), 1800.0, 5000.0, targetPercent = 85)
        assertEquals(ChargingPhase.CV, cvPhase)
    }

    @Test
    fun `elevated battery temperature and heating slope apply thermal throttling factor and record ETA history`() {
        val coolPredictor = ChargingEtaPredictor()
        val warmPredictor = ChargingEtaPredictor()

        var coolEtaMs: Long? = null
        var warmEta: ChargingEtaEstimate? = null

        for (i in 0..3) {
            val t = i * 30_000L
            val soc = 50f + i * 0.5f
            coolEtaMs = coolPredictor.update(
                snapshot = snapshotAt(t, soc, 3000.0, tempC = 32.0f),
                smoothedCurrentMa = 3000.0,
                effectiveCapacityMah = 5000.0,
                targetPercent = 80,
                thermalSlopeCPerMin = 0.2f
            ).first?.timeTo100Ms

            warmEta = warmPredictor.update(
                snapshot = snapshotAt(t, soc, 3000.0, tempC = 44.5f),
                smoothedCurrentMa = 3000.0,
                effectiveCapacityMah = 5000.0,
                targetPercent = 80,
                thermalSlopeCPerMin = 1.4f
            ).first
        }

        assertNotNull(coolEtaMs)
        assertNotNull(warmEta?.timeTo100Ms)
        assertTrue(warmEta!!.isThermalThrottlingAdjusted)
        assertTrue("Expected thermal throttling to extend ETA", warmEta.timeTo100Ms!! > coolEtaMs!!)
        assertTrue("Expected prediction history to record points", warmEta.predictionHistory.isNotEmpty())
    }

    private fun snapshotAt(
        timestampMs: Long,
        levelPrecise: Float,
        currentMa: Double?,
        tempC: Float = 32.0f
    ): BatterySnapshot {
        return BatterySnapshot(
            timestampMs = timestampMs,
            levelPercent = levelPrecise.toInt(),
            levelPrecisePercent = levelPrecise,
            chargingStatus = ChargingStatus.CHARGING,
            chargingSource = ChargingSource.AC,
            healthCondition = BatteryHealthCondition.GOOD,
            isBatteryPresent = true,
            isBatteryLow = false,
            technology = "Li-ion",
            temperatureCelsius = tempC,
            voltageMv = 4350,
            currentNowMa = currentMa,
            currentAverageMa = currentMa,
            signedCurrentMa = currentMa,
            isNetBatteryDischargingWhilePlugged = false,
            chargeCounterUah = 2_500_000L,
            energyCounterNwh = null,
            cycleCount = null,
            hardwareStateOfHealthPercent = null,
            designCapacityMah = 5000.0,
            chargeTimeRemainingMs = null
        )
    }
}
