package com.batteryintelligence.domain.intelligence

import com.batteryintelligence.domain.model.UsageTimelineBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUsageSessionReconstructorTest {

    private val hourMs = 3_600_000L
    private val windowStart = 1_700_000_000_000L
    private val windowEnd = windowStart + 24L * hourMs

    @Test
    fun `coalesces multi-activity switches inside the same package into one continuous session`() {
        val events = listOf(
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 10_000L),
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 40_000L),
            NormalizedUsageEvent("com.example.chat", NormalizedUsageEventType.FOREGROUND_EXIT, windowStart + 130_000L)
        )

        val result = AppUsageSessionReconstructor.reconstruct(
            events = events,
            windowStartMs = windowStart,
            windowEndMs = windowEnd
        )

        assertEquals(1, result.sessions.size)
        assertEquals(120_000L, result.sessions.first().durationMs)
    }

    @Test
    fun `merges micro-interruptions under 3 seconds for the same package`() {
        val events = listOf(
            NormalizedUsageEvent("com.example.browser", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 10_000L),
            NormalizedUsageEvent("com.example.browser", NormalizedUsageEventType.FOREGROUND_EXIT, windowStart + 70_000L),
            // 2-second interruption (<= 3s threshold)
            NormalizedUsageEvent("com.example.browser", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 72_000L),
            NormalizedUsageEvent("com.example.browser", NormalizedUsageEventType.FOREGROUND_EXIT, windowStart + 132_000L)
        )

        val result = AppUsageSessionReconstructor.reconstruct(
            events = events,
            windowStartMs = windowStart,
            windowEndMs = windowEnd
        )

        assertEquals(1, result.sessions.size)
        assertEquals(122_000L, result.sessions.first().durationMs)
    }

    @Test
    fun `handles out-of-order events, window start clipping, and window end clipping`() {
        val events = listOf(
            // Unsorted order: exit before enter, plus active session continuing past windowEnd
            NormalizedUsageEvent("com.example.docs", NormalizedUsageEventType.FOREGROUND_EXIT, windowStart + 60_000L),
            NormalizedUsageEvent("com.example.docs", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart - 30_000L),
            NormalizedUsageEvent("com.example.music", NormalizedUsageEventType.FOREGROUND_ENTER, windowEnd - 120_000L)
        )

        val result = AppUsageSessionReconstructor.reconstruct(
            events = events,
            windowStartMs = windowStart,
            windowEndMs = windowEnd
        )

        assertEquals(2, result.sessions.size)
        val first = result.sessions[0]
        assertEquals("com.example.docs", first.packageName)
        assertEquals(windowStart, first.startTimeMs)
        assertEquals(60_000L, first.durationMs)
        assertTrue(first.wasClippedAtWindowStart)

        val second = result.sessions[1]
        assertEquals("com.example.music", second.packageName)
        assertEquals(windowEnd, second.endTimeMs)
        assertEquals(120_000L, second.durationMs)
        assertTrue(second.wasClippedAtWindowEnd)
    }

    @Test
    fun `closes active session cleanly on screen off and device reboot and produces inactivity gaps`() {
        val events = listOf(
            NormalizedUsageEvent(null, NormalizedUsageEventType.SCREEN_ON, windowStart + 60_000L),
            NormalizedUsageEvent("com.example.maps", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 60_000L),
            NormalizedUsageEvent(null, NormalizedUsageEventType.SCREEN_OFF, windowStart + 360_000L),
            // 20 minutes later, open app then reboot
            NormalizedUsageEvent("com.example.notes", NormalizedUsageEventType.FOREGROUND_ENTER, windowStart + 1_560_000L),
            NormalizedUsageEvent(null, NormalizedUsageEventType.DEVICE_SHUTDOWN, windowStart + 1_860_000L)
        )

        val result = AppUsageSessionReconstructor.reconstruct(
            events = events,
            windowStartMs = windowStart,
            windowEndMs = windowEnd
        )

        assertEquals(2, result.sessions.size)
        assertEquals("Screen Off", result.sessions[0].closeReason)
        assertEquals(300_000L, result.sessions[0].durationMs)
        assertEquals("Device Shutdown", result.sessions[1].closeReason)
        assertEquals(300_000L, result.sessions[1].durationMs)
        assertEquals(24, result.hourlyBuckets.size)
        assertEquals(600_000L, result.hourlyBuckets[0].totalForegroundMs)
        assertTrue(result.timelineBlocks.any { it is UsageTimelineBlock.InactivityGapBlock })
    }
}
