package com.batteryintelligence.domain.repository

import android.graphics.drawable.Drawable
import com.batteryintelligence.domain.model.AppPowerUsageSummary
import com.batteryintelligence.domain.model.Rolling24HourUsageReport

interface AppUsageRepository {
    fun hasUsageStatsPermission(): Boolean

    suspend fun getAppUsageAttribution(
        windowStartMs: Long,
        windowEndMs: Long
    ): List<AppPowerUsageSummary>

    suspend fun getRolling24HourUsageReport(
        nowMs: Long = System.currentTimeMillis()
    ): Rolling24HourUsageReport

    fun getCachedAppIcon(packageName: String): Drawable?
}