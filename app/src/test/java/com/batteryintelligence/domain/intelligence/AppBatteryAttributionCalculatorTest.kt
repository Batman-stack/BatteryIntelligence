package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.AppUsageSortMode
import com.batteryintelligence.domain.model.BatteryAttributionState
import com.batteryintelligence.domain.model.BatterySampleRecord
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBatteryAttributionCalculatorTest {

    private val windowStart = 1_700_000_000_000L
    private val windowEnd = windowStart + 24L * 3_600_000L

    private fun sampleReconstruction(): SessionReconstructionResult {
        val events = listOf(
            // App A: 1 long session (60 min)
            NormalizedUsageEvent("com.example.video", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 1_000_000L),
            NormalizedUsageEvent("com.example.video", NormalizedUsageEventType.FOREGROUND_EXIT, windowStart + 4_600_000L),
            // App B: 3 short sessions (5 min each = 15 min total, most recent)
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 5_000_000L),
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_EXIT, windowStart + 5_300_000L),
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 6_000_000L),
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_EXIT, windowStart + 6_300_000L),
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 7_000_000L),
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_EXIT, windowStart + 7_300_000L)
        )
        return AppUsageSessionReconstructor.reconstruct(
            events = events,
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            appNameResolver = { if (it.endsWith("video")) "Video" else "Chat" }
        )
    }

    @Test
    fun `returns UNAVAILABLE with null drain share when discharge telemetry is insufficient`() {
        val report = AppBatteryAttributionCalculator.buildRollingReport(
            reconstruction = sampleReconstruction(),
            batterySamples = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd
        )

        assertEquals(BatteryAttributionState.UNAVAILABLE, report.attributionState)
        assertTrue(report.apps.all { it.batteryAttribution.state == BatteryAttributionState.UNAVAILABLE })
        assertTrue(report.apps.all { it.batteryAttribution.estimatedShareOfDrainPercent == null })
        assertTrue(report.apps.all { it.batteryAttribution.estimatedRangeLabel == "Not Available" })
    }

    @Test
    fun `returns ESTIMATED attribution with bounded ranges when discharge samples exist`() {
        val samples = listOf(
            dischargeSample(windowStart + 1_000_000L, 90),
            dischargeSample(windowStart + 4_600_000L, 76),
            dischargeSample(windowStart + 7_300_000L, 70)
        )
        val report = AppBatteryAttributionCalculator.buildRollingReport(
            reconstruction = sampleReconstruction(),
            batterySamples = samples,
            windowStartMs = windowStart,
            windowEndMs = windowEnd
        )

        assertEquals(BatteryAttributionState.ESTIMATED, report.attributionState)
        assertEquals(20, report.totalDeviceDischargePercent)
        val video = report.apps.first { it.packageName == "com.example.video" }
        val chat = report.apps.first { it.packageName == "com.example.chat" }
        assertNotNull(video.batteryAttribution.estimatedShareOfDrainPercent)
        assertNotNull(chat.batteryAttribution.estimatedShareOfDrainPercent)
        assertTrue(video.batteryAttribution.estimatedShareOfDrainPercent!! > chat.batteryAttribution.estimatedShareOfDrainPercent!!)
        assertTrue(video.batteryAttribution.estimatedRangeLabel?.contains("est.") == true)
    }

    @Test
    fun `returns ACTUAL attribution only when direct system API values are explicitly provided`() {
        val report = AppBatteryAttributionCalculator.buildRollingReport(
            reconstruction = sampleReconstruction(),
            batterySamples = emptyList(),
            windowStartMs = windowStart,
            windowEndMs = windowEnd,
            directApiPerAppDrainPercent = mapOf(
                "com.example.video" to 12.5f,
                "com.example.chat" to 3.2f
            )
        )

        assertEquals(BatteryAttributionState.ACTUAL, report.attributionState)
        val video = report.apps.first { it.packageName == "com.example.video" }
        assertEquals(12.5f, video.batteryAttribution.estimatedShareOfDrainPercent!!, 0.01f)
        assertTrue(video.batteryAttribution.estimatedRangeLabel?.contains("reported") == true)
    }

    @Test
    fun `supports all five sort modes deterministically`() {
        val samples = listOf(
            dischargeSample(windowStart + 1_000_000L, 90),
            dischargeSample(windowStart + 7_300_000L, 70)
        )
        val apps = AppBatteryAttributionCalculator.buildRollingReport(
            reconstruction = sampleReconstruction(),
            batterySamples = samples,
            windowStartMs = windowStart,
            windowEndMs = windowEnd
        ).apps

        assertEquals("com.example.video", AppBatteryAttributionCalculator.sortApps(apps, AppUsageSortMode.MOST_USED).first().packageName)
        assertEquals("com.example.chat", AppBatteryAttributionCalculator.sortApps(apps, AppUsageSortMode.LEAST_USED).first().packageName)
        assertEquals("com.example.chat", AppBatteryAttributionCalculator.sortApps(apps, AppUsageSortMode.MOST_FREQUENTLY_OPENED).first().packageName)
        assertEquals("com.example.chat", AppBatteryAttributionCalculator.sortApps(apps, AppUsageSortMode.RECENTLY_OPENED).first().packageName)
        assertEquals("com.example.video", AppBatteryAttributionCalculator.sortApps(apps, AppUsageSortMode.HIGHEST_ESTIMATED_BATTERY).first().packageName)
    }

    private fun dischargeSample(timestampMs: Long, levelPercent: Int): BatterySampleRecord {
        return BatterySampleRecord(
            timestampMs = timestampMs,
            levelPercent = levelPercent,
            chargingStatus = ChargingStatus.DISCHARGING,
            chargingSource = ChargingSource.NONE,
            temperatureCelsius = 31.0f,
            voltageMv = 4000,
            currentNowMa = -450.0,
            powerWatts = 1.8,
            chargeCounterUah = null
        )
    }
}
