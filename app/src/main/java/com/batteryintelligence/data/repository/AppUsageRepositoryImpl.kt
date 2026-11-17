package com.batteryintelligence.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.batteryintelligence.domain.model.AppPowerUsageSummary
import com.batteryintelligence.domain.repository.AppUsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUsageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AppUsageRepository {

    override fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override suspend fun getAppUsageAttribution(
        windowStartMs: Long,
        windowEndMs: Long
    ): List<AppPowerUsageSummary> = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) return@withContext emptyList()

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext emptyList()
        val packageManager = context.packageManager

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            windowStartMs,
            windowEndMs
        ) ?: return@withContext emptyList()

        val activeStats = stats.filter { it.totalTimeInForeground > 0L }
        val totalForegroundTimeMs = activeStats.sumOf { it.totalTimeInForeground }

        if (totalForegroundTimeMs == 0L) return@withContext emptyList()

        activeStats
            .sortedByDescending { it.totalTimeInForeground }
            .take(30)
            .map { usage ->
                val appName = try {
                    val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        packageManager.getApplicationInfo(usage.packageName, PackageManager.ApplicationInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getApplicationInfo(usage.packageName, 0)
                    }
                    packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    usage.packageName
                }

                val share = (usage.totalTimeInForeground.toFloat() / totalForegroundTimeMs.toFloat()) * 100f
                AppPowerUsageSummary(
                    packageName = usage.packageName,
                    appName = appName,
                    foregroundTimeMs = usage.totalTimeInForeground,
                    totalVisibleTimeMs = usage.totalTimeVisible,
                    estimatedSharePercent = share,
                    lastTimeUsedMs = usage.lastTimeUsed
                )
            }
    }
}
