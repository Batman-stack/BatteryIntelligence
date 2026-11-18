package com.batteryintelligence.domain.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ThermalSlopeAnalyzerTest {

    @Test
    fun `returns null before 60 seconds of samples accumulate`() {
        val analyzer = ThermalSlopeAnalyzer()
        assertNull(analyzer.update(0L, 30.0f))
        assertNull(analyzer.update(30_000L, 30.5f))
    }

    @Test
    fun `computes accurate linear regression slope in celsius per minute`() {
        val analyzer = ThermalSlopeAnalyzer()
        // +1.5°C per minute steady rise over 2 minutes
        analyzer.update(0L, 30.0f)
        analyzer.update(30_000L, 30.75f)
        analyzer.update(60_000L, 31.5f)
        analyzer.update(90_000L, 32.25f)
        val slope = analyzer.update(120_000L, 33.0f)

        assertNotNull(slope)
        assertEquals(1.5f, slope!!, 0.05f)
    }

    @Test
    fun `resets window after large sampling gap`() {
        val analyzer = ThermalSlopeAnalyzer()
        analyzer.update(0L, 30.0f)
        analyzer.update(60_000L, 32.0f)
        // 15 minute gap > 10m reset threshold
        val afterGap = analyzer.update(960_000L, 35.0f)
        assertNull(afterGap)
    }
}
