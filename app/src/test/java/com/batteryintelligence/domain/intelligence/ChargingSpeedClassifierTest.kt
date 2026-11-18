package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.BatteryHealthCondition
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.ChargingStatus
import com.batteryintelligence.domain.model.SpeedTierThresholds
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ChargingSpeedClassifierTest {

    private lateinit var classifier: ChargingSpeedClassifier

    @Before
    fun setUp() {
        classifier = ChargingSpeedClassifier()
    }

    @Test
    fun `initial samples stay CALCULATING until minimum window met then settle to FAST`() {
        val r1 = classifier.update(snapshotAt(1_000L, voltageMv = 4400, currentMa = 5000.0)) // 22W
        val r2 = classifier.update(snapshotAt(4_000L, voltageMv = 4400, currentMa = 5000.0))
        assertEquals(ChargingSpeedTier.CALCULATING, r1.speedTier)
        assertEquals(ChargingSpeedTier.CALCULATING, r2.speedTier)

        val r3 = classifier.update(snapshotAt(7_000L, voltageMv = 4400, currentMa = 5000.0))
        assertEquals(ChargingSpeedTier.FAST, r3.speedTier)
    }

    @Test
    fun `isolated spike does not flicker speed tier due to median filter and 12s dwell timer`() {
        // Establish steady NORMAL charging at 12W (4000mV * 3000mA)
        classifier.update(snapshotAt(1_000L, voltageMv = 4000, currentMa = 3000.0))
        classifier.update(snapshotAt(4_000L, voltageMv = 4000, currentMa = 3000.0))
        val baseline = classifier.update(snapshotAt(7_000L, voltageMv = 4000, currentMa = 3000.0))
        assertEquals(ChargingSpeedTier.NORMAL, baseline.speedTier)

        // Single huge spike to 40W should be rejected by median & dwell timer
        val spike = classifier.update(snapshotAt(10_000L, voltageMv = 8000, currentMa = 5000.0))
        assertEquals(ChargingSpeedTier.NORMAL, spike.speedTier)

        // Back to 12W
        val recovered = classifier.update(snapshotAt(13_000L, voltageMv = 4000, currentMa = 3000.0))
        assertEquals(ChargingSpeedTier.NORMAL, recovered.speedTier)
    }

    @Test
    fun `sustained power upgrade transitions only after exceeding deadband and 12s dwell`() {
        classifier.update(snapshotAt(0L, voltageMv = 4000, currentMa = 3000.0)) // 12W
        classifier.update(snapshotAt(3_000L, voltageMv = 4000, currentMa = 3000.0))
        assertEquals(ChargingSpeedTier.NORMAL, classifier.update(snapshotAt(6_000L, voltageMv = 4000, currentMa = 3000.0)).speedTier)

        // Ramp to 26W (above 18W + 1.5W deadband = 19.5W)
        var t = 9_000L
        var result = classifier.update(snapshotAt(t, voltageMv = 4333, currentMa = 6000.0))
        while (t < 36_000L) {
            t += 3_000L
            result = classifier.update(snapshotAt(t, voltageMv = 4333, currentMa = 6000.0))
        }
        assertEquals(ChargingSpeedTier.FAST, result.speedTier)
    }

    @Test
    fun `unplugging resets state and returns DISCHARGING immediately`() {
        classifier.update(snapshotAt(0L, voltageMv = 4400, currentMa = 5000.0))
        classifier.update(snapshotAt(3_000L, voltageMv = 4400, currentMa = 5000.0))
        assertEquals(ChargingSpeedTier.FAST, classifier.update(snapshotAt(6_000L, voltageMv = 4400, currentMa = 5000.0)).speedTier)

        val unplugged = classifier.update(
            snapshotAt(
                timestampMs = 9_000L,
                voltageMv = 4100,
                currentMa = -400.0,
                status = ChargingStatus.DISCHARGING,
                source = ChargingSource.NONE
            )
        )
        assertEquals(ChargingSpeedTier.DISCHARGING, unplugged.speedTier)
    }

    @Test
    fun `not charging while plugged reports PAUSED_HOLD`() {
        val paused = classifier.update(
            snapshotAt(
                timestampMs = 5_000L,
                voltageMv = 4200,
                currentMa = 0.0,
                status = ChargingStatus.NOT_CHARGING,
                source = ChargingSource.AC
            )
        )
        assertEquals(ChargingSpeedTier.PAUSED_HOLD, paused.speedTier)
    }

    @Test
    fun `custom thresholds classify 22W as VERY_FAST when threshold lowered to 20W`() {
        val custom = SpeedTierThresholds(normalMinWatts = 5.0, fastMinWatts = 12.0, veryFastMinWatts = 20.0)
        classifier.update(snapshotAt(0L, voltageMv = 4400, currentMa = 5000.0), custom)
        classifier.update(snapshotAt(3_000L, voltageMv = 4400, currentMa = 5000.0), custom)
        val r = classifier.update(snapshotAt(6_000L, voltageMv = 4400, currentMa = 5000.0), custom)
        assertEquals(ChargingSpeedTier.VERY_FAST, r.speedTier)
    }

    private fun snapshotAt(
        timestampMs: Long,
        voltageMv: Int,
        currentMa: Double,
        status: ChargingStatus = ChargingStatus.CHARGING,
        source: ChargingSource = ChargingSource.AC
    ): BatterySnapshot {
        return BatterySnapshot(
            timestampMs = timestampMs,
            levelPercent = 40,
            levelPrecisePercent = 40f,
            chargingStatus = status,
            chargingSource = source,
            healthCondition = BatteryHealthCondition.GOOD,
            isBatteryPresent = true,
            isBatteryLow = false,
            technology = "Li-ion",
            temperatureCelsius = 30.0f,
            voltageMv = voltageMv,
            currentNowMa = currentMa,
            currentAverageMa = currentMa,
            signedCurrentMa = currentMa,
            isNetBatteryDischargingWhilePlugged = false,
            chargeCounterUah = 2_000_000L,
            energyCounterNwh = null,
            cycleCount = null,
            hardwareStateOfHealthPercent = null,
            designCapacityMah = 5000.0,
            chargeTimeRemainingMs = null
        )
    }
}
