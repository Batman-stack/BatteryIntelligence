package com.batteryintelligence.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.LruCache
import com.batteryintelligence.data.local.dao.AppUsageSessionDao
import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.entity.AppUsageSessionEntity
import com.batteryintelligence.data.local.mapper.toDomain
import com.batteryintelligence.domain.intelligence.AppBatteryAttributionCalculator
import com.batteryintelligence.domain.intelligence.AppUsageSessionReconstructor
import com.batteryintelligence.domain.intelligence.NormalizedUsageEvent
import com.batteryintelligence.domain.intelligence.NormalizedUsageEventType
import com.batteryintelligence.domain.model.AppPowerUsageSummary
import com.batteryintelligence.domain.model.BatteryAttributionState
import com.batteryintelligence.domain.model.ChargingConfidence
import com.batteryintelligence.domain.model.Rolling24HourUsageReport
import com.batteryintelligence.domain.repository.AppUsageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUsageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batterySampleDao: BatterySampleDao,
    private val appUsageSessionDao: AppUsageSessionDao
) : AppUsageRepository {

    private val appLabelCache = LruCache<String, String>(200)
    private val appIconCache = LruCache<String, Drawable>(64)

    override fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun getCachedAppIcon(packageName: String): Drawable? {
        appIconCache.get(packageName)?.let { return it }
        return runCatching {
            val icon = context.packageManager.getApplicationIcon(packageName)
            appIconCache.put(packageName, icon)
            icon
        }.getOrNull()
    }

    override suspend fun getAppUsageAttribution(
        windowStartMs: Long,
        windowEndMs: Long
    ): List<AppPowerUsageSummary> {
        val report = getRolling24HourUsageReport(nowMs = windowEndMs)
        return report.apps.map { detail ->
            AppPowerUsageSummary(
                packageName = detail.packageName,
                appName = detail.appName,
                foregroundTimeMs = detail.totalForegroundMs,
                totalVisibleTimeMs = detail.totalForegroundMs,
                estimatedSharePercent = detail.usageSharePercent,
                lastTimeUsedMs = detail.lastUsedMs
            )
        }
    }

    override suspend fun getRolling24HourUsageReport(
        nowMs: Long
    ): Rolling24HourUsageReport = withContext(Dispatchers.IO) {
        val windowStartMs = nowMs - ROLLING_24H_MS

        if (!hasUsageStatsPermission()) {
            return@withContext Rolling24HourUsageReport(
                windowStartMs = windowStartMs,
                windowEndMs = nowMs,
                generatedAtMs = nowMs,
                totalTrackedForegroundMs = 0L,
                totalDetectedSessions = 0,
                totalDeviceDischargePercent = null,
                screenOnDischargePercent = null,
                screenOnTimeMs = 0L,
                apps = emptyList(),
                timelineBlocks = emptyList(),
                hourlyBuckets = emptyList(),
                attributionState = BatteryAttributionState.UNAVAILABLE,
                attributionConfidence = ChargingConfidence.LOW,
                methodologyNote = "Usage Access permission is required to read rolling 24-hour foreground sessions locally on your device."
            )
        }

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext emptyReport(windowStartMs, nowMs, "Android UsageStatsManager is unavailable on this device.")

        // Query 2 hours before windowStart to capture sessions that started before windowStart and cross the boundary
        val queryStartMs = windowStartMs - PRE_WINDOW_LOOKBACK_MS
        val rawEvents = mutableListOf<NormalizedUsageEvent>()
        val usageEvents = runCatching { usageStatsManager.queryEvents(queryStartMs, nowMs) }.getOrNull()
        if (usageEvents != null) {
            val event = UsageEvents.Event()
            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                val normalizedType = mapUsageEventType(event.eventType) ?: continue
                rawEvents.add(
                    NormalizedUsageEvent(
                        packageName = event.packageName,
                        eventType = normalizedType,
                        timestampMs = event.timeStamp
                    )
                )
            }
        }

        val reconstruction = withContext(Dispatchers.Default) {
            AppUsageSessionReconstructor.reconstruct(
                events = rawEvents,
                windowStartMs = windowStartMs,
                windowEndMs = nowMs,
                appNameResolver = ::resolveAppLabel
            )
        }

        // Persist reconstructed sessions to Room cache and prune >7d entries
        runCatching {
            appUsageSessionDao.deleteFromWindow(windowStartMs)
            if (reconstruction.sessions.isNotEmpty()) {
                val entities = reconstruction.sessions.map { s ->
                    AppUsageSessionEntity(
                        packageName = s.packageName,
                        appName = s.appName,
                        startTimeMs = s.startTimeMs,
                        endTimeMs = s.endTimeMs,
                        durationMs = s.durationMs,
                        closeReason = s.closeReason,
                        recordedAtMs = nowMs
                    )
                }
                appUsageSessionDao.insertSessions(entities)
            }
            appUsageSessionDao.deleteOlderThan(nowMs - CACHE_RETENTION_MS)
        }

        val batterySamples = batterySampleDao.getSamplesBetween(windowStartMs, nowMs)
            .map { it.toDomain() }

        withContext(Dispatchers.Default) {
            AppBatteryAttributionCalculator.buildRollingReport(
                reconstruction = reconstruction,
                batterySamples = batterySamples,
                windowStartMs = windowStartMs,
                windowEndMs = nowMs,
                directApiPerAppDrainPercent = null
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun mapUsageEventType(eventType: Int): NormalizedUsageEventType? {
        return when (eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED,
            UsageEvents.Event.MOVE_TO_FOREGROUND -> NormalizedUsageEventType.FOREGROUND_ENTER

            UsageEvents.Event.ACTIVITY_PAUSED,
            UsageEvents.Event.ACTIVITY_STOPPED,
            UsageEvents.Event.MOVE_TO_BACKGROUND -> NormalizedUsageEventType.FOREGROUND_EXIT

            UsageEvents.Event.SCREEN_INTERACTIVE -> NormalizedUsageEventType.SCREEN_ON
            UsageEvents.Event.SCREEN_NON_INTERACTIVE -> NormalizedUsageEventType.SCREEN_OFF
            UsageEvents.Event.KEYGUARD_SHOWN -> NormalizedUsageEventType.KEYGUARD_SHOWN
            UsageEvents.Event.DEVICE_SHUTDOWN -> NormalizedUsageEventType.DEVICE_SHUTDOWN
            UsageEvents.Event.DEVICE_STARTUP -> NormalizedUsageEventType.DEVICE_STARTUP
            else -> null
        }
    }

    private fun resolveAppLabel(packageName: String): String {
        appLabelCache.get(packageName)?.let { return it }
        val resolved = runCatching {
            val pm = context.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
            pm.getApplicationLabel(appInfo).toString().takeIf { it.isNotBlank() } ?: packageName
        }.getOrDefault(packageName)
        appLabelCache.put(packageName, resolved)
        return resolved
    }

    private fun emptyReport(startMs: Long, endMs: Long, note: String): Rolling24HourUsageReport {
        return Rolling24HourUsageReport(
            windowStartMs = startMs,
            windowEndMs = endMs,
            generatedAtMs = endMs,
            totalTrackedForegroundMs = 0L,
            totalDetectedSessions = 0,
            totalDeviceDischargePercent = null,
            screenOnDischargePercent = null,
            screenOnTimeMs = 0L,
            apps = emptyList(),
            timelineBlocks = emptyList(),
            hourlyBuckets = emptyList(),
            attributionState = BatteryAttributionState.UNAVAILABLE,
            attributionConfidence = ChargingConfidence.LOW,
            methodologyNote = note
        )
    }

    private companion object {
        const val ROLLING_24H_MS = 24L * 3_600_000L
        const val PRE_WINDOW_LOOKBACK_MS = 2L * 3_600_000L
        const val CACHE_RETENTION_MS = 7L * 24L * 3_600_000L
    }
}
