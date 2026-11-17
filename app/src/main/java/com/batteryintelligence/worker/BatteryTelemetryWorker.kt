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
import com.batteryintelligence.data.local.mapper.toSampleEntity
import com.batteryintelligence.data.session.ChargingSessionManager
import com.batteryintelligence.data.session.OvernightLogManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class BatteryTelemetryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val batteryDataSource: BatteryHardwareDataSource,
    private val batterySampleDao: BatterySampleDao,
    private val chargingSessionManager: ChargingSessionManager,
    private val overnightLogManager: OvernightLogManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return runCatching {
            val snapshot = batteryDataSource.readSnapshot()
            val latest = batterySampleDao.getLatestSample()

            val shouldPersist = latest == null ||
                (snapshot.timestampMs - latest.timestampMs) >= MIN_PERSIST_GAP_MS ||
                latest.levelPercent != snapshot.levelPercent ||
                latest.chargingSource != snapshot.chargingSource ||
                latest.chargingStatus != snapshot.chargingStatus

            if (shouldPersist) {
                batterySampleDao.insertSample(snapshot.toSampleEntity())
            }

            chargingSessionManager.processSnapshot(snapshot)
            overnightLogManager.evaluateOvernightWindow(snapshot.timestampMs)

            val retentionCutoff = snapshot.timestampMs - SAMPLE_RETENTION_MS
            batterySampleDao.deleteOlderThan(retentionCutoff)

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