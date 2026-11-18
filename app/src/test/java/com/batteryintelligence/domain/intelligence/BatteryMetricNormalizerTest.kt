package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.data.datasource.BatteryMetricNormalizer
import com.batteryintelligence.domain.model.BatteryHealthCondition
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryMetricNormalizerTest {

    @Test
    fun `normalizeCurrentToMilliamps converts standard microampere readings above 30k`() {
        assertEquals(2500.0, BatteryMetricNormalizer.normalizeCurrentToMilliamps(2_500_000L)!!, 0.01)
        assertEquals(-420.0, BatteryMetricNormalizer.normalizeCurrentToMilliamps(-420_000L)!!, 0.01)
    }

    @Test
    fun `normalizeCurrentToMilliamps handles 10k to 30k ambiguity using chargeCounter hint`() {
        // Without hint, 12_000 is treated as 12_000 uA -> 12 mA
        assertEquals(12.0, BatteryMetricNormalizer.normalizeCurrentToMilliamps(12_000L)!!, 0.01)

        // With high-current >10A charge counter hint (~11.5A), 12_000 is preserved as 12,000 mA
        val dualCellBoost = BatteryMetricNormalizer.normalizeCurrentToMilliamps(
            rawValue = 12_000L,
            chargeCounterRateMaHint = 11_500.0
        )
        assertEquals(12_000.0, dualCellBoost!!, 0.01)
    }

    @Test
    fun `normalizeCurrentToMilliamps rejects hardware sentinel values and zero`() {
        assertNull(BatteryMetricNormalizer.normalizeCurrentToMilliamps(Int.MIN_VALUE.toLong()))
        assertNull(BatteryMetricNormalizer.normalizeCurrentToMilliamps(Long.MIN_VALUE))
        assertNull(BatteryMetricNormalizer.normalizeCurrentToMilliamps(0L))
    }

    @Test
    fun `normalizeVoltageToMillivolts supports dual-cell series packs and microvolt outliers`() {
        assertEquals(4250, BatteryMetricNormalizer.normalizeVoltageToMillivolts(4250))
        assertEquals(8650, BatteryMetricNormalizer.normalizeVoltageToMillivolts(8650))
        assertEquals(4320, BatteryMetricNormalizer.normalizeVoltageToMillivolts(4_320_000))
        assertNull(BatteryMetricNormalizer.normalizeVoltageToMillivolts(Int.MIN_VALUE))
        assertNull(BatteryMetricNormalizer.normalizeVoltageToMillivolts(400))
    }

    @Test
    fun `normalizeTemperatureCelsius rejects isolated 0C thermistor dropout when warm baseline exists`() {
        assertEquals(33.4f, BatteryMetricNormalizer.normalizeTemperatureCelsius(334, 32.0f)!!, 0.01f)
        assertNull(BatteryMetricNormalizer.normalizeTemperatureCelsius(0, previousValidTempCelsius = 30.0f))
        assertEquals(0.0f, BatteryMetricNormalizer.normalizeTemperatureCelsius(0, previousValidTempCelsius = 2.0f)!!, 0.01f)
    }

    @Test
    fun `calibratePolarity normalizes inverted OEM sign and flags net drain while plugged`() {
        // Inverted OEM reports negative current during CHARGING (polarityPositive = false)
        val (chargingCurrent, netDrain) = BatteryMetricNormalizer.calibratePolarity(
            rawCurrentMa = -3000.0,
            chargingStatus = ChargingStatus.CHARGING,
            chargingPolarityPositive = false
        )
        assertEquals(3000.0, chargingCurrent!!, 0.01)
        assertFalse(netDrain)

        // Heavy gaming load on weak USB charger causes positive drain while OEM polarityPositive = false
        val (drainCurrent, isNetDrain) = BatteryMetricNormalizer.calibratePolarity(
            rawCurrentMa = 450.0,
            chargingStatus = ChargingStatus.CHARGING,
            chargingPolarityPositive = false
        )
        assertEquals(-450.0, drainCurrent!!, 0.01)
        assertTrue(isNetDrain)
    }

    @Test
    fun `snapshot powerWatts follows canonical voltage x current formula and zeroes net drain while plugged`() {
        val chargingSnapshot = sampleSnapshot(
            voltageMv = 4400,
            currentNowMa = 5000.0,
            isNetDrain = false
        )
        assertNotNull(chargingSnapshot.powerWatts)
        assertEquals(22.0, chargingSnapshot.powerWatts!!, 0.001)

        val netDrainSnapshot = sampleSnapshot(
            voltageMv = 4000,
            currentNowMa = -500.0,
            isNetDrain = true
        )
        assertEquals(0.0, netDrainSnapshot.powerWatts!!, 0.001)
    }

    private fun sampleSnapshot(
        voltageMv: Int?,
        currentNowMa: Double?,
        isNetDrain: Boolean
    ): BatterySnapshot {
        return BatterySnapshot(
            timestampMs = 1_000_000L,
            levelPercent = 55,
            levelPrecisePercent = 55f,
            chargingStatus = ChargingStatus.CHARGING,
            chargingSource = ChargingSource.AC,
            healthCondition = BatteryHealthCondition.GOOD,
            isBatteryPresent = true,
            isBatteryLow = false,
            technology = "Li-ion",
            temperatureCelsius = 31.0f,
            voltageMv = voltageMv,
            currentNowMa = currentNowMa,
            currentAverageMa = currentNowMa,
            signedCurrentMa = currentNowMa,
            isNetBatteryDischargingWhilePlugged = isNetDrain,
            chargeCounterUah = 2_500_000L,
            energyCounterNwh = null,
            cycleCount = null,
            hardwareStateOfHealthPercent = null,
            designCapacityMah = 5000.0,
            chargeTimeRemainingMs = null
        )
    }
}
