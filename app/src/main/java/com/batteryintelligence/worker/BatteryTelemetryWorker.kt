package com.batteryintelligence.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.batteryintelligence.data.datasource.BatteryHardwareDataSource
import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.dao.ChargingSessionSampleDao
import com.batteryintelligence.data.local.mapper.toSampleEntity
import com.batteryintelligence.data.session.ChargingSessionManager
import com.batteryintelligence.data.session.OvernightLogManager
import com.batteryintelligence.domain.intelligence.ChargingIntelligenceEngine
import com.batteryintelligence.domain.repository.PreferencesRepository
import com.batteryintelligence.widget.BatteryWidgetUpdater
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class BatteryTelemetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val batteryHardwareDataSource: BatteryHardwareDataSource,
    private val batterySampleDao: BatterySampleDao,
    private val chargingSessionSampleDao: ChargingSessionSampleDao,
    private val chargingSessionManager: ChargingSessionManager,
    private val overnightLogManager: OvernightLogManager,
    private val chargingIntelligenceEngine: ChargingIntelligenceEngine,
    private val preferencesRepository: PreferencesRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val snapshot = batteryHardwareDataSource.readSnapshot()
            val prefs = preferencesRepository.preferences.first()
            val intelligence = chargingIntelligenceEngine.processSnapshot(
                snapshot = snapshot,
                preferences = prefs,
                effectiveCapacityMah = snapshot.designCapacityMah
            )

            val latest = batterySampleDao.getLatestSample()
            val shouldPersist = latest == null ||
                (snapshot.timestampMs - latest.timestampMs) >= MIN_PERSIST_GAP_MS ||
                latest.levelPercent != snapshot.levelPercent ||
                latest.chargingSource != snapshot.chargingSource ||
                latest.chargingStatus != snapshot.chargingStatus

            if (shouldPersist) {
                batterySampleDao.insertSample(snapshot.toSampleEntity())
            }

            chargingSessionManager.processSnapshot(snapshot, intelligence)
            overnightLogManager.evaluateOvernightWindow(snapshot.timestampMs)

            val rawSampleCutoff = snapshot.timestampMs - SAMPLE_RETENTION_MS
            batterySampleDao.deleteOlderThan(rawSampleCutoff)

            val curveSampleCutoff = snapshot.timestampMs - SESSION_CURVE_RETENTION_MS
            chargingSessionSampleDao.deleteOlderThan(curveSampleCutoff)
            chargingSessionSampleDao.pruneBeyondRecentSessions(KEEP_RECENT_CURVE_SESSIONS)

            BatteryWidgetUpdater.updateAllWidgets(applicationContext)
            Result.success()
        }.getOrElse {
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "battery_telemetry_periodic_logger"
        const val ONE_TIME_WORK_NAME = "battery_telemetry_immediate_snapshot"

        private const val MIN_PERSIST_GAP_MS = 30_000L
        private const val SAMPLE_RETENTION_MS = 30L * 24L * 60L * 60_000L
        private const val SESSION_CURVE_RETENTION_MS = 90L * 24L * 60L * 60_000L
        private const val KEEP_RECENT_CURVE_SESSIONS = 100
        private const val MAX_RETRY_ATTEMPTS = 3

        fun schedulePeriodicLogging(
            workManager: WorkManager,
            repeatIntervalMinutes: Long = 15L
        ) {
            val safeIntervalMinutes = repeatIntervalMinutes.coerceAtLeast(15L)
            val request = PeriodicWorkRequestBuilder<BatteryTelemetryWorker>(
                repeatInterval = safeIntervalMinutes,
                repeatIntervalTimeUnit = TimeUnit.MINUTES
            )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30L,
                    TimeUnit.SECONDS
                )
                .build()

            workManager.enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueImmediateSnapshot(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<BatteryTelemetryWorker>().build()
            workManager.enqueue(request)
        }
    }
}