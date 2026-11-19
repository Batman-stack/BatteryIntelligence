package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.AppBatteryAttribution
import com.batteryintelligence.domain.model.AppUsageDetail
import com.batteryintelligence.domain.model.AppUsageSortMode
import com.batteryintelligence.domain.model.BatteryAttributionState
import com.batteryintelligence.domain.model.BatterySampleRecord
import com.batteryintelligence.domain.model.ChargingConfidence
import com.batteryintelligence.domain.model.ChargingStatus
import com.batteryintelligence.domain.model.ReconstructedAppSession
import com.batteryintelligence.domain.model.Rolling24HourUsageReport
import java.util.Locale
import kotlin.math.roundToInt

data class DischargeTelemetryWindowSummary(
    val totalDischargePercent: Int,
    val screenOnDischargePercent: Int,
    val sampleCount: Int
)

/**
 * Strict 3-state per-app battery attribution model (ACTUAL / ESTIMATED / UNAVAILABLE).
 *
 * Public Android 12-17 SDK APIs do not expose direct per-app hardware mAh consumption to
 * non-privileged apps. Therefore:
 * - ACTUAL is returned ONLY if a future documented public API explicitly supplies direct per-app values.
 * - ESTIMATED combines observed device discharge over the rolling 24h window with app foreground
 *   active duration share and reports a bounded relative range + confidence score—never fake mAh.
 * - UNAVAILABLE is returned whenever discharge telemetry is insufficient (< 2% net discharge or < 2 samples)
 *   or no foreground sessions were observed.
 */
object AppBatteryAttributionCalculator {

    private const val HOUR_MS = 3_600_000L

    fun summarizeDeviceDischarge(
        samples: List<BatterySampleRecord>,
        sessions: List<ReconstructedAppSession>
    ): DischargeTelemetryWindowSummary {
        if (samples.size < 2) {
            return DischargeTelemetryWindowSummary(0, 0, samples.size)
        }
        val ordered = samples.sortedBy { it.timestampMs }
        var totalDrop = 0
        var activeWindowDrop = 0

        for (i in 1 until ordered.size) {
            val prev = ordered[i - 1]
            val curr = ordered[i]
            val prevLvl = prev.levelPercent ?: continue
            val currLvl = curr.levelPercent ?: continue
            val isDischarging = curr.chargingStatus == ChargingStatus.DISCHARGING || !curr.chargingSource.isPlugged
            if (isDischarging && prevLvl > currLvl) {
                val drop = prevLvl - currLvl
                totalDrop += drop
                val overlapsApp = sessions.any { s ->
                    s.endTimeMs >= prev.timestampMs && s.startTimeMs <= curr.timestampMs
                }
                if (overlapsApp) {
                    activeWindowDrop += drop
                }
            }
        }

        return DischargeTelemetryWindowSummary(
            totalDischargePercent = totalDrop,
            screenOnDischargePercent = activeWindowDrop,
            sampleCount = ordered.size
        )
    }

    fun buildRollingReport(
        reconstruction: SessionReconstructionResult,
        batterySamples: List<BatterySampleRecord>,
        windowStartMs: Long,
        windowEndMs: Long,
        directApiPerAppDrainPercent: Map<String, Float>? = null
    ): Rolling24HourUsageReport {
        val discharge = summarizeDeviceDischarge(batterySamples, reconstruction.sessions)
        val totalForegroundMs = reconstruction.sessions.sumOf { it.durationMs }

        val overallState = when {
            !directApiPerAppDrainPercent.isNullOrEmpty() -> BatteryAttributionState.ACTUAL
            totalForegroundMs > 0L && discharge.sampleCount >= 2 && discharge.totalDischargePercent >= 2 ->
                BatteryAttributionState.ESTIMATED
            else -> BatteryAttributionState.UNAVAILABLE
        }

        val overallConfidence = when (overallState) {
            BatteryAttributionState.ACTUAL -> ChargingConfidence.HIGH
            BatteryAttributionState.ESTIMATED -> if (discharge.sampleCount >= 12 && discharge.totalDischargePercent >= 10) {
                ChargingConfidence.MEDIUM
            } else {
                ChargingConfidence.LOW
            }
            BatteryAttributionState.UNAVAILABLE -> ChargingConfidence.LOW
        }

        val methodology = when (overallState) {
            BatteryAttributionState.ACTUAL ->
                "Direct per-app battery attribution provided by public Android system API."
            BatteryAttributionState.ESTIMATED ->
                "Public Android 12–17 APIs do not expose hardware mAh per app. Values estimate each app's relative share of active screen-on battery drain (${discharge.totalDischargePercent}% total device discharge across ${discharge.sampleCount} telemetry samples) weighted by foreground duration. Background wakeups, GPU load, and modem bursts are not individually metered."
            BatteryAttributionState.UNAVAILABLE ->
                "Insufficient battery discharge telemetry recorded during this 24-hour window (needs ≥2% net discharge across ≥2 samples). Keep monitoring active to enable estimated attribution."
        }

        val grouped = reconstruction.sessions.groupBy { it.packageName }
        val unsortedDetails = grouped.map { (pkg, pkgSessions) ->
            val orderedSessions = pkgSessions.sortedBy { it.startTimeMs }
            val appTotalMs = orderedSessions.sumOf { it.durationMs }
            val sharePct = if (totalForegroundMs > 0L) {
                ((appTotalMs.toDouble() / totalForegroundMs.toDouble()) * 100.0).toFloat()
            } else {
                0f
            }
            val hourly = computePerAppHourlyDurations(orderedSessions, windowStartMs)

            val attribution = buildPerAppAttribution(
                packageName = pkg,
                usageSharePercent = sharePct,
                overallState = overallState,
                overallConfidence = overallConfidence,
                discharge = discharge,
                directApiDrain = directApiPerAppDrainPercent?.get(pkg),
                methodology = methodology
            )

            AppUsageDetail(
                packageName = pkg,
                appName = orderedSessions.last().appName,
                totalForegroundMs = appTotalMs,
                detectedSessionCount = orderedSessions.size,
                firstUsedMs = orderedSessions.first().startTimeMs,
                lastUsedMs = orderedSessions.last().endTimeMs,
                averageSessionMs = if (orderedSessions.isNotEmpty()) appTotalMs / orderedSessions.size else 0L,
                longestSessionMs = orderedSessions.maxOfOrNull { it.durationMs } ?: 0L,
                usageSharePercent = sharePct,
                dailyRank = 0,
                hourlyForegroundMs = hourly,
                sessions = orderedSessions,
                batteryAttribution = attribution
            )
        }

        val ranked = unsortedDetails
            .sortedByDescending { it.totalForegroundMs }
            .mapIndexed { index, detail -> detail.copy(dailyRank = index + 1) }

        return Rolling24HourUsageReport(
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            generatedAtMs = windowEndMs,
            totalTrackedForegroundMs = totalForegroundMs,
            totalDetectedSessions = reconstruction.sessions.size,
            totalDeviceDischargePercent = discharge.totalDischargePercent.takeIf { discharge.sampleCount >= 2 },
            screenOnDischargePercent = discharge.screenOnDischargePercent.takeIf { discharge.sampleCount >= 2 },
            screenOnTimeMs = reconstruction.screenOnTimeMs,
            apps = ranked,
            timelineBlocks = reconstruction.timelineBlocks,
            hourlyBuckets = reconstruction.hourlyBuckets,
            attributionState = overallState,
            attributionConfidence = overallConfidence,
            methodologyNote = methodology
        )
    }

    fun sortApps(
        apps: List<AppUsageDetail>,
        sortMode: AppUsageSortMode
    ): List<AppUsageDetail> {
        return when (sortMode) {
            AppUsageSortMode.MOST_USED ->
                apps.sortedWith(compareByDescending<AppUsageDetail> { it.totalForegroundMs }.thenBy { it.appName })
            AppUsageSortMode.LEAST_USED ->
                apps.sortedWith(compareBy<AppUsageDetail> { it.totalForegroundMs }.thenBy { it.appName })
            AppUsageSortMode.MOST_FREQUENTLY_OPENED ->
                apps.sortedWith(compareByDescending<AppUsageDetail> { it.detectedSessionCount }.thenByDescending { it.totalForegroundMs })
            AppUsageSortMode.RECENTLY_OPENED ->
                apps.sortedWith(compareByDescending<AppUsageDetail> { it.lastUsedMs }.thenByDescending { it.totalForegroundMs })
            AppUsageSortMode.HIGHEST_ESTIMATED_BATTERY ->
                apps.sortedWith(
                    compareByDescending<AppUsageDetail> { it.batteryAttribution.estimatedShareOfDrainPercent ?: -1f }
                        .thenByDescending { it.totalForegroundMs }
                )
        }
    }

    private fun buildPerAppAttribution(
        packageName: String,
        usageSharePercent: Float,
        overallState: BatteryAttributionState,
        overallConfidence: ChargingConfidence,
        discharge: DischargeTelemetryWindowSummary,
        directApiDrain: Float?,
        methodology: String
    ): AppBatteryAttribution {
        return when (overallState) {
            BatteryAttributionState.ACTUAL -> {
                val pct = directApiDrain ?: 0f
                AppBatteryAttribution(
                    state = BatteryAttributionState.ACTUAL,
                    estimatedShareOfDrainPercent = pct,
                    estimatedRangeLabel = String.format(Locale.US, "%.1f%% reported", pct),
                    confidence = ChargingConfidence.HIGH,
                    methodologySummary = methodology
                )
            }

            BatteryAttributionState.ESTIMATED -> {
                // Separate active screen-on drain fraction from standby drain
                val activeBasePct = if (discharge.screenOnDischargePercent > 0) {
                    discharge.screenOnDischargePercent.toFloat()
                } else {
                    (discharge.totalDischargePercent * 0.70f)
                }
                val estimatedPctPoints = (activeBasePct * (usageSharePercent / 100f)).coerceAtLeast(0f)
                val lowBound = (estimatedPctPoints * 0.75f).coerceAtLeast(0.1f)
                val highBound = (estimatedPctPoints * 1.25f).coerceAtLeast(lowBound + 0.2f)
                val rangeLabel = if (estimatedPctPoints < 0.5f) {
                    "< 1% est. drain"
                } else {
                    String.format(
                        Locale.US,
                        "~%d–%d%% est. drain",
                        lowBound.roundToInt().coerceAtLeast(1),
                        highBound.roundToInt().coerceAtLeast(lowBound.roundToInt() + 1)
                    )
                }
                AppBatteryAttribution(
                    state = BatteryAttributionState.ESTIMATED,
                    estimatedShareOfDrainPercent = estimatedPctPoints,
                    estimatedRangeLabel = rangeLabel,
                    confidence = overallConfidence,
                    methodologySummary = methodology
                )
            }

            BatteryAttributionState.UNAVAILABLE -> AppBatteryAttribution(
                state = BatteryAttributionState.UNAVAILABLE,
                estimatedShareOfDrainPercent = null,
                estimatedRangeLabel = "Not Available",
                confidence = ChargingConfidence.LOW,
                methodologySummary = methodology
            )
        }
    }

    private fun computePerAppHourlyDurations(
        sessions: List<ReconstructedAppSession>,
        windowStartMs: Long
    ): List<Long> {
        val buckets = LongArray(24)
        for (session in sessions) {
            var cursor = session.startTimeMs
            while (cursor < session.endTimeMs) {
                val idx = (((cursor - windowStartMs) / HOUR_MS).toInt()).coerceIn(0, 23)
                val bucketEnd = windowStartMs + (idx + 1) * HOUR_MS
                val segEnd = minOf(session.endTimeMs, bucketEnd)
                buckets[idx] += (segEnd - cursor).coerceAtLeast(0L)
                if (segEnd <= cursor) break
                cursor = segEnd
            }
        }
        return buckets.toList()
    }
}
