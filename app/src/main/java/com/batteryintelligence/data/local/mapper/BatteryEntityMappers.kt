package com.batteryintelligence.data.local.mapper

import com.batteryintelligence.data.local.entity.BatterySampleEntity
import com.batteryintelligence.data.local.entity.ChargingSessionEntity
import com.batteryintelligence.data.local.entity.ChargingSessionSampleEntity
import com.batteryintelligence.data.local.entity.DailyBatteryRollupEntity
import com.batteryintelligence.data.local.entity.OvernightLogEntity
import com.batteryintelligence.domain.model.BatterySampleRecord
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.ChargingSessionSample
import com.batteryintelligence.domain.model.DailyBatteryRollup
import com.batteryintelligence.domain.model.OvernightLog

fun BatterySnapshot.toSampleEntity(): BatterySampleEntity {
    return BatterySampleEntity(
        timestampMs = timestampMs,
        levelPercent = levelPercent,
        chargingStatus = chargingStatus,
        chargingSource = chargingSource,
        temperatureCelsius = temperatureCelsius,
        voltageMv = voltageMv,
        currentNowMa = currentNowMa,
        powerWatts = powerWatts,
        chargeCounterUah = chargeCounterUah,
        energyCounterNwh = energyCounterNwh
    )
}

fun BatterySampleEntity.toDomain(): BatterySampleRecord {
    return BatterySampleRecord(
        id = id,
        timestampMs = timestampMs,
        levelPercent = levelPercent,
        chargingStatus = chargingStatus,
        chargingSource = chargingSource,
        temperatureCelsius = temperatureCelsius,
        voltageMv = voltageMv,
        currentNowMa = currentNowMa,
        powerWatts = powerWatts,
        chargeCounterUah = chargeCounterUah
    )
}

fun ChargingSessionEntity.toDomain(): ChargingSession {
    val averageTemp = avgTemperatureCelsius ?: if (temperatureSampleCount > 0) {
        (temperatureSumCelsius / temperatureSampleCount).toFloat()
    } else {
        null
    }
    val computedDuration = if (durationMs > 0L) {
        durationMs
    } else {
        ((endTimeMs ?: lastSampleTimeMs) - startTimeMs).coerceAtLeast(0L)
    }
    val computedAvgCurrent = avgCurrentMa ?: if (telemetrySampleCount > 0) {
        currentSumMa / telemetrySampleCount
    } else {
        null
    }
    val computedAvgPower = avgPowerWatts ?: if (telemetrySampleCount > 0) {
        powerSumWatts / telemetrySampleCount
    } else {
        null
    }
    return ChargingSession(
        id = id,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        startLevelPercent = startLevelPercent,
        endLevelPercent = endLevelPercent,
        durationMs = computedDuration,
        chargingSource = chargingSource,
        avgCurrentMa = computedAvgCurrent,
        peakCurrentMa = peakCurrentMa,
        avgPowerWatts = computedAvgPower,
        peakPowerWatts = peakPowerWatts,
        peakTemperatureCelsius = maxTemperatureCelsius,
        averageTemperatureCelsius = averageTemp,
        minTemperatureCelsius = minTemperatureCelsius,
        peakThermalSlopeCPerMin = peakThermalSlopeCPerMin,
        chargeAddedUah = chargeAddedUah,
        energyAddedWh = energyAddedWh,
        chargingEfficiencyPercent = chargingEfficiencyPercent,
        estimatedCapacityMah = estimatedCapacityMah,
        dominantSpeedTier = dominantSpeedTier,
        ccCvTransitionPercent = ccCvTransitionPercent,
        ccCvTransitionTimeMs = ccCvTransitionTimeMs,
        terminationReason = terminationReason,
        initialEta100Ms = initialEta100Ms,
        etaError100Ms = etaError100Ms,
        isActive = isActive
    )
}

fun ChargingSessionSampleEntity.toDomain(): ChargingSessionSample {
    return ChargingSessionSample(
        id = id,
        sessionId = sessionId,
        timestampMs = timestampMs,
        elapsedMs = elapsedMs,
        levelPercent = levelPercent,
        levelPrecisePercent = levelPrecisePercent,
        voltageMv = voltageMv,
        currentNowMa = currentNowMa,
        smoothedPowerWatts = smoothedPowerWatts,
        temperatureCelsius = temperatureCelsius,
        thermalSlopeCPerMin = thermalSlopeCPerMin,
        speedTier = speedTier,
        chargingPhase = chargingPhase,
        chargeCounterUah = chargeCounterUah
    )
}

fun DailyBatteryRollupEntity.toDomain(): DailyBatteryRollup {
    return DailyBatteryRollup(
        dateIso = dateIso,
        sessionCount = sessionCount,
        totalChargingDurationMs = totalChargingDurationMs,
        totalChargeAddedMah = totalChargeAddedMah,
        totalEnergyAddedWh = totalEnergyAddedWh,
        avgPowerWatts = avgPowerWatts,
        maxPowerWatts = maxPowerWatts,
        avgTemperatureCelsius = avgTemperatureCelsius,
        maxTemperatureCelsius = maxTemperatureCelsius,
        totalDischargePercent = totalDischargePercent,
        avgOvernightDrainPctPerHr = avgOvernightDrainPctPerHr,
        estimatedCapacityMahAvg = estimatedCapacityMahAvg,
        updatedAtMs = updatedAtMs
    )
}

fun OvernightLogEntity.toDomain(): OvernightLog {
    return OvernightLog(
        nightDateIso = nightDateIso,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        durationMs = durationMs,
        startLevelPercent = startLevelPercent,
        endLevelPercent = endLevelPercent,
        batteryDropPercent = batteryDropPercent,
        drainRatePercentPerHour = drainRatePercentPerHour,
        chargeDrainedUah = chargeDrainedUah,
        minTemperatureCelsius = minTemperatureCelsius,
        maxTemperatureCelsius = maxTemperatureCelsius,
        wasPluggedIn = wasPluggedIn,
        sampleCount = sampleCount
    )
}