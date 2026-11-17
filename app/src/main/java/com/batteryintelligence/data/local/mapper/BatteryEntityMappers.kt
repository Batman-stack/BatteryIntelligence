package com.batteryintelligence.data.local.mapper

import com.batteryintelligence.data.local.entity.BatterySampleEntity
import com.batteryintelligence.data.local.entity.ChargingSessionEntity
import com.batteryintelligence.data.local.entity.OvernightLogEntity
import com.batteryintelligence.domain.model.BatterySampleRecord
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingSession
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
    val averageTemp = if (temperatureSampleCount > 0) {
        (temperatureSumCelsius / temperatureSampleCount).toFloat()
    } else {
        null
    }

    return ChargingSession(
        id = id,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs,
        startLevelPercent = startLevelPercent,
        endLevelPercent = endLevelPercent,
        chargingSource = chargingSource,
        peakCurrentMa = peakCurrentMa,
        peakPowerWatts = peakPowerWatts,
        peakTemperatureCelsius = maxTemperatureCelsius,
        averageTemperatureCelsius = averageTemp,
        chargeAddedUah = chargeAddedUah,
        estimatedCapacityMah = estimatedCapacityMah,
        isActive = isActive,
        minTemperatureCelsius = minTemperatureCelsius,
        energyAddedWh = energyAddedWh,
        chargingEfficiencyPercent = chargingEfficiencyPercent
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