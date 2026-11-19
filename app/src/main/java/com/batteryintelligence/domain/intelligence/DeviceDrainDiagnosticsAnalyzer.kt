package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.BatteryHealthReport
import com.batteryintelligence.domain.model.DiagnosticSeverity
import com.batteryintelligence.domain.model.DisplayAndSotDiagnostics
import com.batteryintelligence.domain.model.DrainDiagnosticInsight
import com.batteryintelligence.domain.model.MemoryStorageDiagnostics
import com.batteryintelligence.domain.model.OvernightLog
import com.batteryintelligence.domain.model.Rolling24HourUsageReport
import com.batteryintelligence.domain.model.SystemProfileInfo
import com.batteryintelligence.domain.model.ThermalSeverityLevel
import java.util.Locale

/**
 * Synthesizes real device telemetry (thermal status, deep-sleep ratio, RAM/storage pressure,
 * rolling 24h foreground app concentration, overnight standby drain, and battery health)
 * into plain-English actionable diagnostic cards.
 */
object DeviceDrainDiagnosticsAnalyzer {

    fun analyze(
        systemProfile: SystemProfileInfo,
        memoryStorage: MemoryStorageDiagnostics,
        displaySot: DisplayAndSotDiagnostics,
        thermalSeverity: ThermalSeverityLevel,
        healthReport: BatteryHealthReport?,
        usageReport: Rolling24HourUsageReport?,
        latestOvernightLog: OvernightLog?
    ): List<DrainDiagnosticInsight> {
        val insights = mutableListOf<DrainDiagnosticInsight>()

        // 1. Thermal status check
        when (thermalSeverity) {
            ThermalSeverityLevel.MODERATE,
            ThermalSeverityLevel.SEVERE,
            ThermalSeverityLevel.CRITICAL,
            ThermalSeverityLevel.EMERGENCY,
            ThermalSeverityLevel.SHUTDOWN -> insights.add(
                DrainDiagnosticInsight(
                    id = "thermal_elevated",
                    title = "Thermal Throttling Risk (${thermalSeverity.displayLabel})",
                    detail = "Android PowerManager reports elevated thermal pressure. High temperatures accelerate lithium-ion wear and slow fast charging.",
                    severity = DiagnosticSeverity.WARNING
                )
            )
            ThermalSeverityLevel.LIGHT -> insights.add(
                DrainDiagnosticInsight(
                    id = "thermal_light",
                    title = "Mild Device Warmth",
                    detail = "System thermal status is Light. Avoid heavy gaming or navigation while fast charging.",
                    severity = DiagnosticSeverity.INFO
                )
            )
            ThermalSeverityLevel.NONE -> insights.add(
                DrainDiagnosticInsight(
                    id = "thermal_nominal",
                    title = "Thermal Headroom Nominal",
                    detail = "No active Android thermal throttling detected.",
                    severity = DiagnosticSeverity.GOOD
                )
            )
            ThermalSeverityLevel.UNAVAILABLE -> Unit
        }

        // 2. Deep-sleep ratio check (when uptime >= 1 hour)
        if (systemProfile.elapsedRealtimeMs >= 3_600_000L) {
            val sleepPct = systemProfile.deepSleepPercent
            if (sleepPct < 30f) {
                insights.add(
                    DrainDiagnosticInsight(
                        id = "deep_sleep_low",
                        title = String.format(Locale.US, "Low Deep Sleep Ratio (%.0f%%)", sleepPct),
                        detail = "The CPU stayed awake for most of the current uptime. Check background wakeups or overnight sync jobs if standby drain feels high.",
                        severity = DiagnosticSeverity.WARNING
                    )
                )
            } else if (sleepPct >= 55f) {
                insights.add(
                    DrainDiagnosticInsight(
                        id = "deep_sleep_good",
                        title = String.format(Locale.US, "Healthy Deep Sleep (%.0f%%)", sleepPct),
                        detail = "Device enters Doze and CPU deep sleep efficiently between active sessions.",
                        severity = DiagnosticSeverity.GOOD
                    )
                )
            }
        }

        // 3. Top 24h foreground app dominance
        val topApp = usageReport?.apps?.firstOrNull()
        if (topApp != null && topApp.usageSharePercent >= 35f && topApp.totalForegroundMs >= 30L * 60_000L) {
            val mins = topApp.totalForegroundMs / 60_000L
            insights.add(
                DrainDiagnosticInsight(
                    id = "top_app_share",
                    title = "${topApp.appName} Dominates 24h Screen Time",
                    detail = String.format(
                        Locale.US,
                        "Accounted for %.0f%% of tracked foreground time (%d min across %d sessions).",
                        topApp.usageSharePercent,
                        mins,
                        topApp.detectedSessionCount
                    ),
                    severity = DiagnosticSeverity.INFO
                )
            )
        }

        // 4. Overnight standby drain
        if (latestOvernightLog != null && !latestOvernightLog.wasPluggedIn) {
            val rate = latestOvernightLog.drainRatePercentPerHour
            if (rate != null && rate >= 1.5f) {
                insights.add(
                    DrainDiagnosticInsight(
                        id = "overnight_drain_high",
                        title = String.format(Locale.US, "Elevated Overnight Drain (%.2f%%/hr)", rate),
                        detail = "Last recorded standby night (${latestOvernightLog.nightDateIso}) lost more than 1.5%/hr. Review weak cellular signal or background refresh.",
                        severity = DiagnosticSeverity.WARNING
                    )
                )
            }
        }

        // 5. High-rate display refresh note
        val hz = displaySot.refreshRateHz
        if (hz != null && hz >= 115f) {
            insights.add(
                DrainDiagnosticInsight(
                    id = "high_refresh_rate",
                    title = String.format(Locale.US, "High Refresh Display (%.0f Hz)", hz),
                    detail = "120Hz+ display modes improve fluidity at higher screen-on power draw. Switching to standard refresh can extend heavy reading/browsing days.",
                    severity = DiagnosticSeverity.INFO
                )
            )
        }

        // 6. Memory / storage pressure
        if (memoryStorage.isLowMemory || memoryStorage.storageUsagePercent >= 90f) {
            insights.add(
                DrainDiagnosticInsight(
                    id = "resource_pressure",
                    title = "Memory or Storage Pressure Detected",
                    detail = String.format(
                        Locale.US,
                        "RAM %.0f%% used • Storage %.0f%% full. Heavy app reloading and low flash headroom increase background CPU work.",
                        memoryStorage.ramUsagePercent,
                        memoryStorage.storageUsagePercent
                    ),
                    severity = DiagnosticSeverity.WARNING
                )
            )
        }

        // 7. Battery health longevity guidance
        if (healthReport != null && healthReport.highTempSessionCount >= 2) {
            insights.add(
                DrainDiagnosticInsight(
                    id = "warm_charging_sessions",
                    title = "${healthReport.highTempSessionCount} Warm Charging Sessions Recorded",
                    detail = "Frequent charging above 40°C accelerates chemical aging. Consider removing thick cases or lowering your target charge alarm to 80–85%.",
                    severity = DiagnosticSeverity.WARNING
                )
            )
        }

        return insights
    }
}
