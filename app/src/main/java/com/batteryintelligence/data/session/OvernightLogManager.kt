package com.batteryintelligence.data.session

import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.dao.OvernightLogDao
import com.batteryintelligence.data.local.entity.OvernightLogEntity
import com.batteryintelligence.domain.model.ChargingStatus
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OvernightLogManager @Inject constructor(
    private val batterySampleDao: BatterySampleDao,
    private val overnightLogDao: OvernightLogDao
) {
    suspend fun evaluateOvernightWindow(referenceTimestampMs: Long) {
        val zoneId = ZoneId.systemDefault()
        val localDateTime = Instant.ofEpochMilli(referenceTimestampMs)
            .atZone(zoneId)
            .toLocalDateTime()
        val hour = localDateTime.hour

        // Evaluate during evening-to-morning window (22:00..08:59) to finalize morning logs cleanly.
        if (hour in 9..21) return

        val nightDate = if (hour >= 22) {
            localDateTime.toLocalDate().plusDays(1)
        } else {
            localDateTime.toLocalDate()
        }

        val windowStartMs = nightDate.minusDays(1)
            .atTime(22, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val windowEndMs = nightDate
            .atTime(7, 0)
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()

        val samples = batterySampleDao.getSamplesBetween(windowStartMs, windowEndMs)
        if (samples.size < 2) return

        val first = samples.first()
        val last = samples.last()
        val durationMs = (last.timestampMs - first.timestampMs).coerceAtLeast(0L)
        if (durationMs < MIN_OVERNIGHT_DURATION_MS) return

        val wasPluggedIn = samples.any {
            it.chargingSource.isPlugged || it.chargingStatus == ChargingStatus.CHARGING
        }

        val startLevel = first.levelPercent
        val endLevel = last.levelPercent

        val batteryDrop = if (!wasPluggedIn && startLevel != null && endLevel != null) {
            (startLevel - endLevel).coerceAtLeast(0)
        } else {
            null
        }

        val drainRatePerHour = if (batteryDrop != null && durationMs > 0L) {
            val hours = durationMs / 3_600_000f
            if (hours > 0f) batteryDrop / hours else null
        } else {
            null
        }

        val chargeDrainedUah = if (
            !wasPluggedIn &&
            first.chargeCounterUah != null &&
            last.chargeCounterUah != null &&
            first.chargeCounterUah >= last.chargeCounterUah
        ) {
            first.chargeCounterUah - last.chargeCounterUah
        } else {
            null
        }

        val temps = samples.mapNotNull { it.temperatureCelsius }

        overnightLogDao.upsertLog(
            OvernightLogEntity(
                nightDateIso = nightDate.toString(),
                startTimeMs = first.timestampMs,
                endTimeMs = last.timestampMs,
                durationMs = durationMs,
                startLevelPercent = startLevel,
                endLevelPercent = endLevel,
                batteryDropPercent = batteryDrop,
                drainRatePercentPerHour = drainRatePerHour,
                chargeDrainedUah = chargeDrainedUah,
                minTemperatureCelsius = temps.minOrNull(),
                maxTemperatureCelsius = temps.maxOrNull(),
                wasPluggedIn = wasPluggedIn,
                sampleCount = samples.size
            )
        )
    }

    private companion object {
        const val MIN_OVERNIGHT_DURATION_MS = 30L * 60_000L
    }
}