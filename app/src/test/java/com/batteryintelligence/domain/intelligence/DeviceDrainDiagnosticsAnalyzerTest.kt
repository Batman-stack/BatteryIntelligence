package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.BatteryHealthReport
import com.batteryintelligence.domain.model.DiagnosticSeverity
import com.batteryintelligence.domain.model.DisplayAndSotDiagnostics
import com.batteryintelligence.domain.model.MemoryStorageDiagnostics
import com.batteryintelligence.domain.model.MetricSource
import com.batteryintelligence.domain.model.OvernightLog
import com.batteryintelligence.domain.model.SystemProfileInfo
import com.batteryintelligence.domain.model.ThermalSeverityLevel
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceDrainDiagnosticsAnalyzerTest {

    @Test
    fun `flags thermal throttling, low deep sleep, high overnight drain, and resource pressure`() {
        val insights = DeviceDrainDiagnosticsAnalyzer.analyze(
            systemProfile = SystemProfileInfo(
                manufacturer = "Google",
                model = "Pixel",
                deviceName = "pixel",
                androidVersion = "15",
                sdkInt = 35,
                securityPatch = "2026-01-05",
                elapsedRealtimeMs = 10L * 3_600_000L,
                awakeUptimeMs = 8L * 3_600_000L,
                deepSleepMs = 2L * 3_600_000L,
                deepSleepPercent = 20f
            ),
            memoryStorage = MemoryStorageDiagnostics(
                totalRamBytes = 8_000_000_000L,
                availableRamBytes = 500_000_000L,
                usedRamBytes = 7_500_000_000L,
                lowMemoryThresholdBytes = 600_000_000L,
                isLowMemory = true,
                internalTotalBytes = 128_000_000_000L,
                internalAvailableBytes = 8_000_000_000L,
                internalUsedBytes = 120_000_000_000L
            ),
            displaySot = DisplayAndSotDiagnostics(
                refreshRateHz = 120f,
                isAdaptiveBrightness = false,
                rolling24hScreenOnMs = 5L * 3_600_000L,
                rolling24hScreenOffMs = 19L * 3_600_000L,
                sinceUnplugScreenOnMs = null
            ),
            thermalSeverity = ThermalSeverityLevel.SEVERE,
            healthReport = BatteryHealthReport(
                designCapacityMah = 5000.0,
                designCapacitySource = MetricSource.USER_CONFIGURED,
                estimatedFullCapacityMah = 4600.0,
                stateOfHealthPercent = 92,
                stateOfHealthSource = MetricSource.SESSION_DERIVED,
                cycleCount = null,
                cycleCountSource = MetricSource.UNAVAILABLE,
                highTempSessionCount = 3,
                evaluatedSessionsCount = 10
            ),
            usageReport = null,
            latestOvernightLog = OvernightLog(
                nightDateIso = "2026-02-01",
                startTimeMs = 0L,
                endTimeMs = 8L * 3_600_000L,
                durationMs = 8L * 3_600_000L,
                startLevelPercent = 80,
                endLevelPercent = 64,
                batteryDropPercent = 16,
                drainRatePercentPerHour = 2.0f,
                chargeDrainedUah = null,
                minTemperatureCelsius = 25f,
                maxTemperatureCelsius = 30f,
                wasPluggedIn = false,
                sampleCount = 16
            )
        )

        val ids = insights.map { it.id }.toSet()
        assertTrue("thermal_elevated" in ids)
        assertTrue("deep_sleep_low" in ids)
        assertTrue("overnight_drain_high" in ids)
        assertTrue("high_refresh_rate" in ids)
        assertTrue("resource_pressure" in ids)
        assertTrue("warm_charging_sessions" in ids)
        assertTrue(insights.any { it.severity == DiagnosticSeverity.WARNING })
    }

    @Test
    fun `reports nominal thermal and healthy deep sleep on well-behaved device`() {
        val insights = DeviceDrainDiagnosticsAnalyzer.analyze(
            systemProfile = SystemProfileInfo(
                manufacturer = "Google",
                model = "Pixel",
                deviceName = "pixel",
                androidVersion = "15",
                sdkInt = 35,
                securityPatch = "2026-01-05",
                elapsedRealtimeMs = 12L * 3_600_000L,
                awakeUptimeMs = 3L * 3_600_000L,
                deepSleepMs = 9L * 3_600_000L,
                deepSleepPercent = 75f
            ),
            memoryStorage = MemoryStorageDiagnostics(
                totalRamBytes = 12_000_000_000L,
                availableRamBytes = 6_000_000_000L,
                usedRamBytes = 6_000_000_000L,
                lowMemoryThresholdBytes = 600_000_000L,
                isLowMemory = false,
                internalTotalBytes = 256_000_000_000L,
                internalAvailableBytes = 140_000_000_000L,
                internalUsedBytes = 116_000_000_000L
            ),
            displaySot = DisplayAndSotDiagnostics(
                refreshRateHz = 60f,
                isAdaptiveBrightness = true,
                rolling24hScreenOnMs = 3L * 3_600_000L,
                rolling24hScreenOffMs = 21L * 3_600_000L,
                sinceUnplugScreenOnMs = null
            ),
            thermalSeverity = ThermalSeverityLevel.NONE,
            healthReport = null,
            usageReport = null,
            latestOvernightLog = null
        )

        assertTrue(insights.any { it.id == "thermal_nominal" && it.severity == DiagnosticSeverity.GOOD })
        assertTrue(insights.any { it.id == "deep_sleep_good" && it.severity == DiagnosticSeverity.GOOD })
        assertTrue(insights.none { it.severity == DiagnosticSeverity.WARNING })
    }
}
