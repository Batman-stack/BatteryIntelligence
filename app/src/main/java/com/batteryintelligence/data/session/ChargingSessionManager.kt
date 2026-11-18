package com.batteryintelligence.data.session

import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.dao.ChargingSessionSampleDao
import com.batteryintelligence.data.local.dao.DailyBatteryRollupDao
import com.batteryintelligence.data.local.entity.ChargingSessionEntity
import com.batteryintelligence.data.local.entity.ChargingSessionSampleEntity
import com.batteryintelligence.data.local.entity.DailyBatteryRollupEntity
import com.batteryintelligence.data.local.mapper.toDomain
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.ChargingPhase
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.ChargingStatus
import com.batteryintelligence.domain.model.ChargingTerminationReason
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToLong

sealed interface SessionTransition {
    data class PluggedIn(val session: ChargingSession) : SessionTransition
    data class ActiveUpdated(val session: ChargingSession) : SessionTransition
    data class Unplugged(val completedSession: ChargingSession) : SessionTransition
    data object Discharging : SessionTransition
}

@Singleton
class ChargingSessionManager @Inject constructor(
    private val chargingSessionDao: ChargingSessionDao,
    private val chargingSessionSampleDao: ChargingSessionSampleDao,
    private val dailyBatteryRollupDao: DailyBatteryRollupDao
) {
    private val sessionMutex = Mutex()
    private val tierVotes = mutableMapOf<ChargingSpeedTier, Int>()

    suspend fun processSnapshot(
        snapshot: BatterySnapshot,
        intelligenceState: ChargingIntelligenceState? = null
    ): SessionTransition = sessionMutex.withLock {
        val activeSession = chargingSessionDao.getActiveSession()
        val isPlugged = snapshot.chargingSource.isPlugged ||
            snapshot.chargingStatus == ChargingStatus.CHARGING

        if (activeSession != null && !isPlugged) {
            val closed = finalizeSession(activeSession, snapshot, intelligenceState, isStale = false)
            chargingSessionDao.updateSession(closed)
            recordSampleIfDue(closed, snapshot, intelligenceState, force = true)
            updateDailyRollupForSession(closed)
            tierVotes.clear()
            return@withLock SessionTransition.Unplugged(closed.toDomain())
        }

        if (!isPlugged) {
            return@withLock SessionTransition.Discharging
        }

        if (activeSession != null &&
            (snapshot.timestampMs - activeSession.lastSampleTimeMs) > SESSION_STALE_GAP_MS
        ) {
            val staleDuration = (activeSession.lastSampleTimeMs - activeSession.startTimeMs).coerceAtLeast(0L)
            val staleClosed = activeSession.copy(
                isActive = false,
                endTimeMs = activeSession.lastSampleTimeMs,
                durationMs = staleDuration,
                terminationReason = ChargingTerminationReason.STALE_GAP_CLOSED
            )
            chargingSessionDao.updateSession(staleClosed)
            updateDailyRollupForSession(staleClosed)
            tierVotes.clear()
            return@withLock startNewSessionIfPossible(snapshot, intelligenceState)?.let { started ->
                recordSampleIfDue(started, snapshot, intelligenceState, force = true)
                SessionTransition.PluggedIn(started.toDomain())
            } ?: SessionTransition.Discharging
        }

        if (activeSession != null &&
            snapshot.chargingSource != ChargingSource.UNKNOWN &&
            activeSession.chargingSource != ChargingSource.UNKNOWN &&
            activeSession.chargingSource != snapshot.chargingSource
        ) {
            val closed = finalizeSession(activeSession, snapshot, intelligenceState, isStale = false)
            chargingSessionDao.updateSession(closed)
            updateDailyRollupForSession(closed)
            tierVotes.clear()
            return@withLock startNewSessionIfPossible(snapshot, intelligenceState)?.let { started ->
                recordSampleIfDue(started, snapshot, intelligenceState, force = true)
                SessionTransition.PluggedIn(started.toDomain())
            } ?: SessionTransition.Unplugged(closed.toDomain())
        }

        if (activeSession == null) {
            tierVotes.clear()
            return@withLock startNewSessionIfPossible(snapshot, intelligenceState)?.let { started ->
                recordSampleIfDue(started, snapshot, intelligenceState, force = true)
                SessionTransition.PluggedIn(started.toDomain())
            } ?: SessionTransition.Discharging
        }

        val updated = updateActiveSession(activeSession, snapshot, intelligenceState)
        chargingSessionDao.updateSession(updated)
        recordSampleIfDue(updated, snapshot, intelligenceState, force = false)
        SessionTransition.ActiveUpdated(updated.toDomain())
    }

    private suspend fun startNewSessionIfPossible(
        snapshot: BatterySnapshot,
        intelligenceState: ChargingIntelligenceState?
    ): ChargingSessionEntity? {
        val startLevel = snapshot.levelPercent ?: return null
        val initialCurrentMa = snapshot.effectiveCurrentMa?.let { abs(it) }
        val initialPowerWatts = intelligenceState?.smoothedPowerWatts ?: snapshot.powerWatts
        val initialTemp = snapshot.temperatureCelsius
        val initialSlope = intelligenceState?.thermalSlopeCPerMin
        val tier = intelligenceState?.speedTier?.takeIf {
            it != ChargingSpeedTier.UNKNOWN && it != ChargingSpeedTier.CALCULATING
        }
        if (tier != null) {
            tierVotes[tier] = 1
        }

        val newEntity = ChargingSessionEntity(
            startTimeMs = snapshot.timestampMs,
            endTimeMs = null,
            lastSampleTimeMs = snapshot.timestampMs,
            startLevelPercent = startLevel,
            endLevelPercent = startLevel,
            chargingSource = snapshot.chargingSource,
            startChargeCounterUah = snapshot.chargeCounterUah,
            endChargeCounterUah = snapshot.chargeCounterUah,
            startEnergyCounterNwh = snapshot.energyCounterNwh,
            endEnergyCounterNwh = snapshot.energyCounterNwh,
            integratedChargeMah = 0.0,
            integratedEnergyWh = 0.0,
            chargeAddedUah = null,
            energyAddedWh = null,
            chargingEfficiencyPercent = null,
            peakCurrentMa = initialCurrentMa,
            peakPowerWatts = initialPowerWatts,
            minTemperatureCelsius = initialTemp,
            maxTemperatureCelsius = initialTemp,
            temperatureSumCelsius = initialTemp?.toDouble() ?: 0.0,
            temperatureSampleCount = if (initialTemp != null) 1 else 0,
            lastCurrentMa = initialCurrentMa,
            lastPowerWatts = initialPowerWatts,
            estimatedCapacityMah = null,
            isActive = true,
            durationMs = 0L,
            currentSumMa = initialCurrentMa ?: 0.0,
            powerSumWatts = initialPowerWatts ?: 0.0,
            telemetrySampleCount = if (initialCurrentMa != null || initialPowerWatts != null) 1 else 0,
            avgCurrentMa = initialCurrentMa,
            avgPowerWatts = initialPowerWatts,
            avgTemperatureCelsius = initialTemp,
            peakThermalSlopeCPerMin = initialSlope,
            terminationReason = ChargingTerminationReason.ACTIVE,
            dominantSpeedTier = tier,
            initialEta100Ms = intelligenceState?.etaEstimate?.timeTo100Ms
        )

        val insertedId = chargingSessionDao.insertSession(newEntity)
        chargingSessionDao.closeOtherActiveSessions(excludeId = insertedId)
        return newEntity.copy(id = insertedId)
    }

    private fun updateActiveSession(
        active: ChargingSessionEntity,
        snapshot: BatterySnapshot,
        intelligenceState: ChargingIntelligenceState?
    ): ChargingSessionEntity {
        val dtMs = (snapshot.timestampMs - active.lastSampleTimeMs).coerceAtLeast(0L)
        val currMa = snapshot.effectiveCurrentMa?.let { abs(it) }
        val currPowerWatts = snapshot.powerWatts
        val smoothedPower = intelligenceState?.smoothedPowerWatts ?: currPowerWatts

        var integratedChargeMah = active.integratedChargeMah
        var integratedEnergyWh = active.integratedEnergyWh

        if (dtMs in 1L..MAX_INTEGRATION_STEP_MS &&
            snapshot.chargingStatus == ChargingStatus.CHARGING &&
            !snapshot.isNetBatteryDischargingWhilePlugged
        ) {
            val dtHours = dtMs / 3_600_000.0
            if (currMa != null) {
                val avgCurrentMa = active.lastCurrentMa?.let { (it + currMa) / 2.0 } ?: currMa
                integratedChargeMah += avgCurrentMa * dtHours
            }
            if (currPowerWatts != null) {
                val avgPowerWatts = active.lastPowerWatts?.let { (it + currPowerWatts) / 2.0 }
                    ?: currPowerWatts
                integratedEnergyWh += avgPowerWatts * dtHours
            }
        }

        val startChargeCounter = active.startChargeCounterUah ?: snapshot.chargeCounterUah
        val endChargeCounter = snapshot.chargeCounterUah ?: active.endChargeCounterUah
        val fuelGaugeChargeAddedUah = if (
            startChargeCounter != null &&
            endChargeCounter != null &&
            endChargeCounter >= startChargeCounter
        ) {
            endChargeCounter - startChargeCounter
        } else {
            null
        }

        val chargeAddedUah = fuelGaugeChargeAddedUah
            ?: integratedChargeMah.takeIf { it > 0.0 }?.let { (it * 1000.0).roundToLong() }

        val startEnergyCounter = active.startEnergyCounterNwh ?: snapshot.energyCounterNwh
        val endEnergyCounter = snapshot.energyCounterNwh ?: active.endEnergyCounterNwh
        val hardwareEnergyAddedWh = if (
            startEnergyCounter != null &&
            endEnergyCounter != null &&
            endEnergyCounter > startEnergyCounter
        ) {
            (endEnergyCounter - startEnergyCounter) / 1_000_000_000.0
        } else {
            null
        }

        val energyAddedWh = hardwareEnergyAddedWh
            ?: integratedEnergyWh.takeIf { it > 0.0 }

        val chargingEfficiencyPercent = calculateCoulombicEfficiencyPercent(
            fuelGaugeChargeAddedUah = fuelGaugeChargeAddedUah,
            integratedChargeMah = integratedChargeMah
        )

        val endLevel = snapshot.levelPercent ?: active.endLevelPercent
        val estimatedCapacityMah = calculateEstimatedCapacityMah(
            startLevelPercent = active.startLevelPercent,
            endLevelPercent = endLevel,
            chargeAddedUah = chargeAddedUah
        )

        val sampleTemp = snapshot.temperatureCelsius
        val minTemp = when {
            sampleTemp == null -> active.minTemperatureCelsius
            active.minTemperatureCelsius == null -> sampleTemp
            else -> minOf(active.minTemperatureCelsius, sampleTemp)
        }
        val maxTemp = when {
            sampleTemp == null -> active.maxTemperatureCelsius
            active.maxTemperatureCelsius == null -> sampleTemp
            else -> maxOf(active.maxTemperatureCelsius, sampleTemp)
        }
        val tempSum = if (sampleTemp != null) active.temperatureSumCelsius + sampleTemp else active.temperatureSumCelsius
        val tempCount = if (sampleTemp != null) active.temperatureSampleCount + 1 else active.temperatureSampleCount
        val avgTemp = if (tempCount > 0) (tempSum / tempCount).toFloat() else null

        val sampleSlope = intelligenceState?.thermalSlopeCPerMin
        val peakSlope = when {
            sampleSlope == null -> active.peakThermalSlopeCPerMin
            active.peakThermalSlopeCPerMin == null -> sampleSlope
            else -> maxOf(active.peakThermalSlopeCPerMin, sampleSlope)
        }

        val peakCurrent = when {
            currMa == null -> active.peakCurrentMa
            active.peakCurrentMa == null -> currMa
            else -> maxOf(active.peakCurrentMa, currMa)
        }
        val peakPower = when {
            smoothedPower == null -> active.peakPowerWatts
            active.peakPowerWatts == null -> smoothedPower
            else -> maxOf(active.peakPowerWatts, smoothedPower)
        }

        val nextTelemetryCount = if (currMa != null || currPowerWatts != null) {
            active.telemetrySampleCount + 1
        } else {
            active.telemetrySampleCount
        }
        val nextCurrentSum = active.currentSumMa + (currMa ?: 0.0)
        val nextPowerSum = active.powerSumWatts + (currPowerWatts ?: 0.0)
        val nextAvgCurrent = if (nextTelemetryCount > 0) nextCurrentSum / nextTelemetryCount else active.avgCurrentMa
        val nextAvgPower = if (nextTelemetryCount > 0) nextPowerSum / nextTelemetryCount else active.avgPowerWatts

        val tier = intelligenceState?.speedTier
        if (tier != null && tier in listOf(
                ChargingSpeedTier.SLOW,
                ChargingSpeedTier.NORMAL,
                ChargingSpeedTier.FAST,
                ChargingSpeedTier.VERY_FAST
            )
        ) {
            tierVotes[tier] = (tierVotes[tier] ?: 0) + 1
        }
        val dominantTier = tierVotes.maxByOrNull { it.value }?.key ?: active.dominantSpeedTier

        val phase = intelligenceState?.chargingPhase
        val ccCvTransitionPercent = active.ccCvTransitionPercent ?: if (
            phase == ChargingPhase.CV || phase == ChargingPhase.TAPER
        ) {
            endLevel
        } else {
            null
        }
        val ccCvTransitionTimeMs = active.ccCvTransitionTimeMs ?: if (
            ccCvTransitionPercent != null
        ) {
            snapshot.timestampMs
        } else {
            null
        }

        val initialEta100 = active.initialEta100Ms ?: intelligenceState?.etaEstimate?.timeTo100Ms
        val durationMs = (snapshot.timestampMs - active.startTimeMs).coerceAtLeast(0L)

        return active.copy(
            lastSampleTimeMs = snapshot.timestampMs,
            endLevelPercent = endLevel,
            durationMs = durationMs,
            startChargeCounterUah = startChargeCounter,
            endChargeCounterUah = endChargeCounter,
            startEnergyCounterNwh = startEnergyCounter,
            endEnergyCounterNwh = endEnergyCounter,
            integratedChargeMah = integratedChargeMah,
            integratedEnergyWh = integratedEnergyWh,
            chargeAddedUah = chargeAddedUah,
            energyAddedWh = energyAddedWh,
            chargingEfficiencyPercent = chargingEfficiencyPercent,
            peakCurrentMa = peakCurrent,
            peakPowerWatts = peakPower,
            minTemperatureCelsius = minTemp,
            maxTemperatureCelsius = maxTemp,
            temperatureSumCelsius = tempSum,
            temperatureSampleCount = tempCount,
            avgTemperatureCelsius = avgTemp,
            peakThermalSlopeCPerMin = peakSlope,
            lastCurrentMa = currMa ?: active.lastCurrentMa,
            lastPowerWatts = currPowerWatts ?: active.lastPowerWatts,
            currentSumMa = nextCurrentSum,
            powerSumWatts = nextPowerSum,
            telemetrySampleCount = nextTelemetryCount,
            avgCurrentMa = nextAvgCurrent,
            avgPowerWatts = nextAvgPower,
            estimatedCapacityMah = estimatedCapacityMah,
            dominantSpeedTier = dominantTier,
            ccCvTransitionPercent = ccCvTransitionPercent,
            ccCvTransitionTimeMs = ccCvTransitionTimeMs,
            initialEta100Ms = initialEta100
        )
    }

    private fun finalizeSession(
        active: ChargingSessionEntity,
        unplugSnapshot: BatterySnapshot,
        intelligenceState: ChargingIntelligenceState?,
        isStale: Boolean
    ): ChargingSessionEntity {
        val updated = updateActiveSession(active, unplugSnapshot, intelligenceState)
        val totalDurationMs = (unplugSnapshot.timestampMs - updated.startTimeMs).coerceAtLeast(0L)
        val reachedFull = (updated.endLevelPercent ?: 0) >= 100 ||
            unplugSnapshot.chargingStatus == ChargingStatus.FULL
        val reason = when {
            isStale -> ChargingTerminationReason.STALE_GAP_CLOSED
            reachedFull -> ChargingTerminationReason.REACHED_FULL
            else -> ChargingTerminationReason.USER_UNPLUGGED
        }
        val etaError = if (reachedFull && updated.initialEta100Ms != null) {
            totalDurationMs - updated.initialEta100Ms
        } else {
            updated.etaError100Ms
        }
        return updated.copy(
            endTimeMs = unplugSnapshot.timestampMs,
            durationMs = totalDurationMs,
            terminationReason = reason,
            etaError100Ms = etaError,
            isActive = false
        )
    }

    private suspend fun recordSampleIfDue(
        session: ChargingSessionEntity,
        snapshot: BatterySnapshot,
        intelligenceState: ChargingIntelligenceState?,
        force: Boolean
    ) {
        val latest = chargingSessionSampleDao.getLatestSampleForSession(session.id)
        val smoothedPower = intelligenceState?.smoothedPowerWatts ?: snapshot.powerWatts
        val elapsedSinceLast = if (latest != null) snapshot.timestampMs - latest.timestampMs else Long.MAX_VALUE
        val levelChanged = latest?.levelPercent != snapshot.levelPercent
        val powerJump = latest?.smoothedPowerWatts != null && smoothedPower != null &&
            abs(latest.smoothedPowerWatts - smoothedPower) >= 2.0

        if (!force && elapsedSinceLast < CURVE_SAMPLE_INTERVAL_MS && !levelChanged && !powerJump) {
            return
        }

        val entity = ChargingSessionSampleEntity(
            sessionId = session.id,
            timestampMs = snapshot.timestampMs,
            elapsedMs = (snapshot.timestampMs - session.startTimeMs).coerceAtLeast(0L),
            levelPercent = snapshot.levelPercent,
            levelPrecisePercent = snapshot.levelPrecisePercent,
            voltageMv = snapshot.voltageMv,
            currentNowMa = snapshot.effectiveCurrentMa?.let { abs(it) },
            smoothedPowerWatts = smoothedPower,
            temperatureCelsius = snapshot.temperatureCelsius,
            thermalSlopeCPerMin = intelligenceState?.thermalSlopeCPerMin,
            speedTier = intelligenceState?.speedTier ?: snapshot.chargingSpeedTier,
            chargingPhase = intelligenceState?.chargingPhase ?: ChargingPhase.UNKNOWN,
            chargeCounterUah = snapshot.chargeCounterUah
        )
        chargingSessionSampleDao.insertSample(entity)
    }

    private suspend fun updateDailyRollupForSession(session: ChargingSessionEntity) {
        val dateIso = Instant.ofEpochMilli(session.startTimeMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
        val existing = dailyBatteryRollupDao.getByDate(dateIso)
        val addedMah = session.chargeAddedUah?.let { it / 1000.0 } ?: 0.0
        val addedWh = session.energyAddedWh ?: 0.0
        val newSessionCount = (existing?.sessionCount ?: 0) + 1
        val newDurationMs = (existing?.totalChargingDurationMs ?: 0L) + session.durationMs
        val newTotalMah = (existing?.totalChargeAddedMah ?: 0.0) + addedMah
        val newTotalWh = (existing?.totalEnergyAddedWh ?: 0.0) + addedWh
        val newMaxPower = listOfNotNull(existing?.maxPowerWatts, session.peakPowerWatts).maxOrNull()
        val newMaxTemp = listOfNotNull(existing?.maxTemperatureCelsius, session.maxTemperatureCelsius).maxOrNull()
        val avgPower = listOfNotNull(existing?.avgPowerWatts, session.avgPowerWatts).takeIf { it.isNotEmpty() }?.average()
        val avgTemp = listOfNotNull(existing?.avgTemperatureCelsius, session.avgTemperatureCelsius)
            .takeIf { it.isNotEmpty() }
            ?.map { it.toDouble() }
            ?.average()
            ?.toFloat()
        val capAvg = listOfNotNull(existing?.estimatedCapacityMahAvg, session.estimatedCapacityMah)
            .takeIf { it.isNotEmpty() }
            ?.average()

        dailyBatteryRollupDao.upsertRollup(
            DailyBatteryRollupEntity(
                dateIso = dateIso,
                sessionCount = newSessionCount,
                totalChargingDurationMs = newDurationMs,
                totalChargeAddedMah = newTotalMah,
                totalEnergyAddedWh = newTotalWh,
                avgPowerWatts = avgPower,
                maxPowerWatts = newMaxPower,
                avgTemperatureCelsius = avgTemp,
                maxTemperatureCelsius = newMaxTemp,
                totalDischargePercent = existing?.totalDischargePercent ?: 0,
                avgOvernightDrainPctPerHr = existing?.avgOvernightDrainPctPerHr,
                estimatedCapacityMahAvg = capAvg,
                updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    private fun calculateCoulombicEfficiencyPercent(
        fuelGaugeChargeAddedUah: Long?,
        integratedChargeMah: Double
    ): Double? {
        if (fuelGaugeChargeAddedUah == null ||
            fuelGaugeChargeAddedUah <= 0L ||
            integratedChargeMah < MIN_INTEGRATED_MAH_FOR_EFFICIENCY
        ) {
            return null
        }
        val storedMah = fuelGaugeChargeAddedUah / 1000.0
        val ratio = storedMah / integratedChargeMah
        if (!ratio.isFinite() || ratio !in 0.20..1.05) {
            return null
        }
        return (ratio * 100.0).coerceIn(0.0, 100.0)
    }

    private fun calculateEstimatedCapacityMah(
        startLevelPercent: Int,
        endLevelPercent: Int?,
        chargeAddedUah: Long?
    ): Double? {
        if (endLevelPercent == null || chargeAddedUah == null || chargeAddedUah <= 0L) {
            return null
        }
        val deltaLevel = endLevelPercent - startLevelPercent
        if (deltaLevel < MIN_LEVEL_DELTA_FOR_CAPACITY_ESTIMATE) {
            return null
        }
        val estimated = (chargeAddedUah / 1000.0) / (deltaLevel / 100.0)
        return estimated.takeIf { it.isFinite() && it in 500.0..25_000.0 }
    }

    private companion object {
        const val SESSION_STALE_GAP_MS = 30L * 60_000L
        const val MAX_INTEGRATION_STEP_MS = 20L * 60_000L
        const val CURVE_SAMPLE_INTERVAL_MS = 15_000L
        const val MIN_INTEGRATED_MAH_FOR_EFFICIENCY = 50.0
        const val MIN_LEVEL_DELTA_FOR_CAPACITY_ESTIMATE = 20
    }
}