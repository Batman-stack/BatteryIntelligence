package com.batteryintelligence.data.session

import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.entity.ChargingSessionEntity
import com.batteryintelligence.data.local.mapper.toDomain
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingStatus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val chargingSessionDao: ChargingSessionDao
) {
    private val sessionMutex = Mutex()

    suspend fun processSnapshot(snapshot: BatterySnapshot): SessionTransition = sessionMutex.withLock {
        val activeSession = chargingSessionDao.getActiveSession()
        val isPlugged = snapshot.chargingSource.isPlugged ||
            snapshot.chargingStatus == ChargingStatus.CHARGING

        if (activeSession != null && !isPlugged) {
            val closed = finalizeSession(activeSession, snapshot)
            chargingSessionDao.updateSession(closed)
            return@withLock SessionTransition.Unplugged(closed.toDomain())
        }

        if (!isPlugged) {
            return@withLock SessionTransition.Discharging
        }

        // If an existing active session has gone stale across a shutdown/reboot gap, close it cleanly.
        if (activeSession != null &&
            (snapshot.timestampMs - activeSession.lastSampleTimeMs) > SESSION_STALE_GAP_MS
        ) {
            val staleClosed = activeSession.copy(
                isActive = false,
                endTimeMs = activeSession.lastSampleTimeMs
            )
            chargingSessionDao.updateSession(staleClosed)
            return@withLock startNewSessionIfPossible(snapshot)?.let {
                SessionTransition.PluggedIn(it.toDomain())
            } ?: SessionTransition.Discharging
        }

        // Split session if the physical charging source changed (e.g., AC -> Wireless) while plugged.
        if (activeSession != null &&
            snapshot.chargingSource != ChargingSource.UNKNOWN &&
            activeSession.chargingSource != ChargingSource.UNKNOWN &&
            activeSession.chargingSource != snapshot.chargingSource
        ) {
            val closed = finalizeSession(activeSession, snapshot)
            chargingSessionDao.updateSession(closed)
            return@withLock startNewSessionIfPossible(snapshot)?.let {
                SessionTransition.PluggedIn(it.toDomain())
            } ?: SessionTransition.Unplugged(closed.toDomain())
        }

        if (activeSession == null) {
            return@withLock startNewSessionIfPossible(snapshot)?.let {
                SessionTransition.PluggedIn(it.toDomain())
            } ?: SessionTransition.Discharging
        }

        val updated = updateActiveSession(activeSession, snapshot)
        chargingSessionDao.updateSession(updated)
        SessionTransition.ActiveUpdated(updated.toDomain())
    }

    private suspend fun startNewSessionIfPossible(snapshot: BatterySnapshot): ChargingSessionEntity? {
        val startLevel = snapshot.levelPercent ?: return null
        val initialCurrentMa = snapshot.currentNowMa?.let { abs(it) }
        val initialTemp = snapshot.temperatureCelsius

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
            peakPowerWatts = snapshot.powerWatts,
            minTemperatureCelsius = initialTemp,
            maxTemperatureCelsius = initialTemp,
            temperatureSumCelsius = initialTemp?.toDouble() ?: 0.0,
            temperatureSampleCount = if (initialTemp != null) 1 else 0,
            lastCurrentMa = initialCurrentMa,
            lastPowerWatts = snapshot.powerWatts,
            estimatedCapacityMah = null,
            isActive = true
        )

        val insertedId = chargingSessionDao.insertSession(newEntity)
        chargingSessionDao.closeOtherActiveSessions(excludeId = insertedId)
        return newEntity.copy(id = insertedId)
    }

    private fun updateActiveSession(
        active: ChargingSessionEntity,
        snapshot: BatterySnapshot
    ): ChargingSessionEntity {
        val dtMs = (snapshot.timestampMs - active.lastSampleTimeMs).coerceAtLeast(0L)
        val currMa = snapshot.currentNowMa?.let { abs(it) }
        val currPowerWatts = snapshot.powerWatts

        var integratedChargeMah = active.integratedChargeMah
        var integratedEnergyWh = active.integratedEnergyWh

        if (dtMs in 1L..MAX_INTEGRATION_STEP_MS &&
            snapshot.chargingStatus == ChargingStatus.CHARGING
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
        val tempSum = if (sampleTemp != null) {
            active.temperatureSumCelsius + sampleTemp
        } else {
            active.temperatureSumCelsius
        }
        val tempCount = if (sampleTemp != null) {
            active.temperatureSampleCount + 1
        } else {
            active.temperatureSampleCount
        }

        val peakCurrent = when {
            currMa == null -> active.peakCurrentMa
            active.peakCurrentMa == null -> currMa
            else -> maxOf(active.peakCurrentMa, currMa)
        }
        val peakPower = when {
            currPowerWatts == null -> active.peakPowerWatts
            active.peakPowerWatts == null -> currPowerWatts
            else -> maxOf(active.peakPowerWatts, currPowerWatts)
        }

        return active.copy(
            lastSampleTimeMs = snapshot.timestampMs,
            endLevelPercent = endLevel,
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
            lastCurrentMa = currMa ?: active.lastCurrentMa,
            lastPowerWatts = currPowerWatts ?: active.lastPowerWatts,
            estimatedCapacityMah = estimatedCapacityMah
        )
    }

    private fun finalizeSession(
        active: ChargingSessionEntity,
        unplugSnapshot: BatterySnapshot
    ): ChargingSessionEntity {
        val updated = updateActiveSession(active, unplugSnapshot)
        return updated.copy(
            endTimeMs = unplugSnapshot.timestampMs,
            isActive = false
        )
    }

    /**
     * Computes honest Coulombic charging efficiency (%) by comparing net stored charge from the
     * hardware fuel gauge (CHARGE_COUNTER delta) against integrated terminal charge (∫ |I_now| dt).
     * Returns null when either signal is unavailable or session volume is too small (< 50 mAh).
     */
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
        const val MIN_INTEGRATED_MAH_FOR_EFFICIENCY = 50.0
        const val MIN_LEVEL_DELTA_FOR_CAPACITY_ESTIMATE = 20
    }
}