package com.batteryintelligence.domain.repository

import com.batteryintelligence.domain.model.AppPowerUsageSummary

interface AppUsageRepository {
    fun hasUsageStatsPermission(): Boolean

    suspend fun getAppUsageAttribution(
        windowStartMs: Long,
        windowEndMs: Long
    ): List<AppPowerUsageSummary>
}