package com.batteryintelligence.domain.model

data class AppBatteryAttribution(
    val state: BatteryAttributionState,
    val estimatedShareOfDrainPercent: Float?,
    val estimatedRangeLabel: String?,
    val confidence: ChargingConfidence,
    val methodologySummary: String
)

data class ReconstructedAppSession(
    val packageName: String,
    val appName: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val durationMs: Long,
    val wasClippedAtWindowStart: Boolean = false,
    val wasClippedAtWindowEnd: Boolean = false,
    val closeReason: String = "Backgrounded"
)

sealed interface UsageTimelineBlock {
    val startTimeMs: Long
    val endTimeMs: Long
    val durationMs: Long

    data class AppSessionBlock(
        val session: ReconstructedAppSession
    ) : UsageTimelineBlock {
        override val startTimeMs: Long get() = session.startTimeMs
        override val endTimeMs: Long get() = session.endTimeMs
        override val durationMs: Long get() = session.durationMs
    }

    data class InactivityGapBlock(
        override val startTimeMs: Long,
        override val endTimeMs: Long,
        override val durationMs: Long,
        val label: String = "Device Inactive / Screen Off"
    ) : UsageTimelineBlock
}

data class HourlyUsageBucket(
    val hourIndex: Int,
    val hourStartMs: Long,
    val totalForegroundMs: Long,
    val detectedSessionCount: Int
)

data class AppUsageDetail(
    val packageName: String,
    val appName: String,
    val totalForegroundMs: Long,
    val detectedSessionCount: Int,
    val firstUsedMs: Long,
    val lastUsedMs: Long,
    val averageSessionMs: Long,
    val longestSessionMs: Long,
    val usageSharePercent: Float,
    val dailyRank: Int,
    val hourlyForegroundMs: List<Long>,
    val sessions: List<ReconstructedAppSession>,
    val batteryAttribution: AppBatteryAttribution
)

data class Rolling24HourUsageReport(
    val windowStartMs: Long,
    val windowEndMs: Long,
    val generatedAtMs: Long,
    val totalTrackedForegroundMs: Long,
    val totalDetectedSessions: Int,
    val totalDeviceDischargePercent: Int?,
    val screenOnDischargePercent: Int?,
    val screenOnTimeMs: Long,
    val apps: List<AppUsageDetail>,
    val timelineBlocks: List<UsageTimelineBlock>,
    val hourlyBuckets: List<HourlyUsageBucket>,
    val attributionState: BatteryAttributionState,
    val attributionConfidence: ChargingConfidence,
    val methodologyNote: String
)
