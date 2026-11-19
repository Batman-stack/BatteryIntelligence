package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.HourlyUsageBucket
import com.batteryintelligence.domain.model.ReconstructedAppSession
import com.batteryintelligence.domain.model.UsageTimelineBlock

enum class NormalizedUsageEventType {
    FOREGROUND_ENTER,
    FOREGROUND_EXIT,
    SCREEN_ON,
    SCREEN_OFF,
    KEYGUARD_SHOWN,
    DEVICE_SHUTDOWN,
    DEVICE_STARTUP
}

data class NormalizedUsageEvent(
    val packageName: String?,
    val eventType: NormalizedUsageEventType,
    val timestampMs: Long
)

data class SessionReconstructionResult(
    val sessions: List<ReconstructedAppSession>,
    val timelineBlocks: List<UsageTimelineBlock>,
    val hourlyBuckets: List<HourlyUsageBucket>,
    val screenOnTimeMs: Long
)

/**
 * Deterministic rolling-24-hour foreground session reconstruction state machine.
 *
 * Key guarantees:
 * 1. Coalesces multi-activity switches inside the same package into 1 continuous session.
 * 2. Merges brief micro-interruptions (< [MERGE_MICRO_GAP_MS], e.g. system dialogs) for the same package.
 * 3. Handles out-of-order timestamps, overlapping apps, screen-off / keyguard / reboot closures,
 *    and exact boundary clipping to [windowStartMs, windowEndMs].
 * 4. Computes 24 rolling hourly buckets and explicit inactivity gaps (>= [MIN_INACTIVITY_GAP_MS]).
 */
object AppUsageSessionReconstructor {

    const val MERGE_MICRO_GAP_MS = 3_000L
    const val MIN_SESSION_DURATION_MS = 1_000L
    const val MIN_INACTIVITY_GAP_MS = 5L * 60_000L
    private const val HOUR_MS = 3_600_000L

    fun reconstruct(
        events: List<NormalizedUsageEvent>,
        windowStartMs: Long,
        windowEndMs: Long,
        appNameResolver: (String) -> String = { it },
        ignoredPackages: Set<String> = emptySet()
    ): SessionReconstructionResult {
        if (windowEndMs <= windowStartMs) {
            return SessionReconstructionResult(
                sessions = emptyList(),
                timelineBlocks = emptyList(),
                hourlyBuckets = buildEmptyHourlyBuckets(windowStartMs),
                screenOnTimeMs = 0L
            )
        }

        val sortedEvents = events
            .filter { it.timestampMs in (windowStartMs - 6L * HOUR_MS)..windowEndMs }
            .sortedBy { it.timestampMs }

        var activePackage: String? = null
        var activeStartMs: Long = 0L
        var activeClippedStart = false

        var screenOnStartMs: Long? = null
        var totalScreenOnMs = 0L
        var sawExplicitScreenEvents = false

        val rawSessions = mutableListOf<ReconstructedAppSession>()

        fun closeActiveSession(closeTimestampMs: Long, reason: String, clippedEnd: Boolean = false) {
            val pkg = activePackage ?: return
            val clampedStart = activeStartMs.coerceAtLeast(windowStartMs)
            val clampedEnd = closeTimestampMs.coerceIn(windowStartMs, windowEndMs)
            val duration = (clampedEnd - clampedStart).coerceAtLeast(0L)
            if (duration >= MIN_SESSION_DURATION_MS && pkg !in ignoredPackages) {
                rawSessions.add(
                    ReconstructedAppSession(
                        packageName = pkg,
                        appName = appNameResolver(pkg),
                        startTimeMs = clampedStart,
                        endTimeMs = clampedEnd,
                        durationMs = duration,
                        wasClippedAtWindowStart = activeClippedStart || activeStartMs < windowStartMs,
                        wasClippedAtWindowEnd = clippedEnd,
                        closeReason = reason
                    )
                )
            }
            activePackage = null
            activeStartMs = 0L
            activeClippedStart = false
        }

        for (ev in sortedEvents) {
            when (ev.eventType) {
                NormalizedUsageEventType.SCREEN_ON -> {
                    sawExplicitScreenEvents = true
                    if (screenOnStartMs == null) {
                        screenOnStartMs = ev.timestampMs.coerceAtLeast(windowStartMs)
                    }
                }

                NormalizedUsageEventType.SCREEN_OFF -> {
                    sawExplicitScreenEvents = true
                    if (screenOnStartMs != null) {
                        val end = ev.timestampMs.coerceIn(windowStartMs, windowEndMs)
                        val start = screenOnStartMs!!.coerceIn(windowStartMs, end)
                        totalScreenOnMs += (end - start).coerceAtLeast(0L)
                        screenOnStartMs = null
                    }
                    closeActiveSession(ev.timestampMs, "Screen Off")
                }

                NormalizedUsageEventType.KEYGUARD_SHOWN -> {
                    closeActiveSession(ev.timestampMs, "Lock Screen")
                }

                NormalizedUsageEventType.DEVICE_SHUTDOWN -> {
                    if (screenOnStartMs != null) {
                        val end = ev.timestampMs.coerceIn(windowStartMs, windowEndMs)
                        val start = screenOnStartMs!!.coerceIn(windowStartMs, end)
                        totalScreenOnMs += (end - start).coerceAtLeast(0L)
                        screenOnStartMs = null
                    }
                    closeActiveSession(ev.timestampMs, "Device Shutdown")
                }

                NormalizedUsageEventType.DEVICE_STARTUP -> {
                    // Close any dangling pre-reboot state cleanly before startup timestamp
                    closeActiveSession(ev.timestampMs, "Device Reboot")
                    screenOnStartMs = null
                }

                NormalizedUsageEventType.FOREGROUND_ENTER -> {
                    val pkg = ev.packageName?.takeIf { it.isNotBlank() } ?: continue
                    if (activePackage == pkg) {
                        // Same package multi-activity switch -> keep single uninterrupted session
                        continue
                    }
                    if (activePackage != null) {
                        closeActiveSession(ev.timestampMs, "Switched App")
                    }
                    activePackage = pkg
                    activeStartMs = ev.timestampMs
                    activeClippedStart = ev.timestampMs < windowStartMs
                }

                NormalizedUsageEventType.FOREGROUND_EXIT -> {
                    val pkg = ev.packageName?.takeIf { it.isNotBlank() } ?: continue
                    if (activePackage == pkg) {
                        closeActiveSession(ev.timestampMs, "Backgrounded")
                    } else if (activePackage == null && ev.timestampMs > windowStartMs && rawSessions.none { it.packageName == pkg && it.endTimeMs >= ev.timestampMs }) {
                        // Ignore orphan background events without a known start to avoid inflating fake duration
                    }
                }
            }
        }

        // Close any session still active at windowEndMs
        if (activePackage != null) {
            closeActiveSession(windowEndMs, "Active Now", clippedEnd = true)
        }
        if (screenOnStartMs != null) {
            val start = screenOnStartMs!!.coerceIn(windowStartMs, windowEndMs)
            totalScreenOnMs += (windowEndMs - start).coerceAtLeast(0L)
        }

        // Merge micro-interruptions (< 3 seconds) for the same package
        val mergedSessions = mergeMicroGaps(rawSessions)
        val timelineBlocks = buildTimelineBlocks(mergedSessions, windowStartMs, windowEndMs)
        val hourlyBuckets = buildHourlyBuckets(mergedSessions, windowStartMs)

        val finalScreenOnMs = if (sawExplicitScreenEvents && totalScreenOnMs > 0L) {
            totalScreenOnMs.coerceAtMost(windowEndMs - windowStartMs)
        } else {
            mergedSessions.sumOf { it.durationMs }.coerceAtMost(windowEndMs - windowStartMs)
        }

        return SessionReconstructionResult(
            sessions = mergedSessions,
            timelineBlocks = timelineBlocks,
            hourlyBuckets = hourlyBuckets,
            screenOnTimeMs = finalScreenOnMs
        )
    }

    private fun mergeMicroGaps(sessions: List<ReconstructedAppSession>): List<ReconstructedAppSession> {
        if (sessions.size <= 1) return sessions
        val merged = mutableListOf<ReconstructedAppSession>()
        for (current in sessions.sortedBy { it.startTimeMs }) {
            val prev = merged.lastOrNull()
            if (prev != null &&
                prev.packageName == current.packageName &&
                current.startTimeMs >= prev.endTimeMs &&
                (current.startTimeMs - prev.endTimeMs) <= MERGE_MICRO_GAP_MS
            ) {
                val combinedEnd = maxOf(prev.endTimeMs, current.endTimeMs)
                merged[merged.lastIndex] = prev.copy(
                    endTimeMs = combinedEnd,
                    durationMs = (combinedEnd - prev.startTimeMs).coerceAtLeast(0L),
                    wasClippedAtWindowEnd = current.wasClippedAtWindowEnd,
                    closeReason = current.closeReason
                )
            } else {
                merged.add(current)
            }
        }
        return merged
    }

    private fun buildTimelineBlocks(
        sessions: List<ReconstructedAppSession>,
        windowStartMs: Long,
        windowEndMs: Long
    ): List<UsageTimelineBlock> {
        if (sessions.isEmpty()) {
            val total = (windowEndMs - windowStartMs).coerceAtLeast(0L)
            return if (total >= MIN_INACTIVITY_GAP_MS) {
                listOf(UsageTimelineBlock.InactivityGapBlock(windowStartMs, windowEndMs, total))
            } else {
                emptyList()
            }
        }

        val blocks = mutableListOf<UsageTimelineBlock>()
        var cursorMs = windowStartMs

        for (session in sessions) {
            val gapMs = session.startTimeMs - cursorMs
            if (gapMs >= MIN_INACTIVITY_GAP_MS) {
                blocks.add(
                    UsageTimelineBlock.InactivityGapBlock(
                        startTimeMs = cursorMs,
                        endTimeMs = session.startTimeMs,
                        durationMs = gapMs
                    )
                )
            }
            blocks.add(UsageTimelineBlock.AppSessionBlock(session))
            cursorMs = maxOf(cursorMs, session.endTimeMs)
        }

        val trailingGap = windowEndMs - cursorMs
        if (trailingGap >= MIN_INACTIVITY_GAP_MS) {
            blocks.add(
                UsageTimelineBlock.InactivityGapBlock(
                    startTimeMs = cursorMs,
                    endTimeMs = windowEndMs,
                    durationMs = trailingGap
                )
            )
        }

        return blocks
    }

    private fun buildHourlyBuckets(
        sessions: List<ReconstructedAppSession>,
        windowStartMs: Long
    ): List<HourlyUsageBucket> {
        val durations = LongArray(24)
        val counts = IntArray(24)

        for (session in sessions) {
            var cursor = session.startTimeMs
            var countedStartHour = -1
            while (cursor < session.endTimeMs) {
                val hourIdx = (((cursor - windowStartMs) / HOUR_MS).toInt()).coerceIn(0, 23)
                val bucketEnd = windowStartMs + (hourIdx + 1) * HOUR_MS
                val segEnd = minOf(session.endTimeMs, bucketEnd)
                val segDur = (segEnd - cursor).coerceAtLeast(0L)
                durations[hourIdx] += segDur
                if (countedStartHour != hourIdx) {
                    counts[hourIdx]++
                    countedStartHour = hourIdx
                }
                if (segEnd <= cursor) break
                cursor = segEnd
            }
        }

        return (0 until 24).map { idx ->
            HourlyUsageBucket(
                hourIndex = idx,
                hourStartMs = windowStartMs + idx * HOUR_MS,
                totalForegroundMs = durations[idx],
                detectedSessionCount = counts[idx]
            )
        }
    }

    private fun buildEmptyHourlyBuckets(windowStartMs: Long): List<HourlyUsageBucket> {
        return (0 until 24).map { idx ->
            HourlyUsageBucket(
                hourIndex = idx,
                hourStartMs = windowStartMs + idx * HOUR_MS,
                totalForegroundMs = 0L,
                detectedSessionCount = 0
            )
        }
    }
}
