package com.batteryintelligence.data.local

import androidx.room.TypeConverter
import com.batteryintelligence.domain.model.ChargingPhase
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.ChargingStatus
import com.batteryintelligence.domain.model.ChargingTerminationReason

class BatteryTypeConverters {

    @TypeConverter
    fun fromChargingStatus(status: ChargingStatus): String = status.name

    @TypeConverter
    fun toChargingStatus(value: String): ChargingStatus {
        return runCatching { ChargingStatus.valueOf(value) }
            .getOrDefault(ChargingStatus.UNKNOWN)
    }

    @TypeConverter
    fun fromChargingSource(source: ChargingSource): String = source.name

    @TypeConverter
    fun toChargingSource(value: String): ChargingSource {
        return runCatching { ChargingSource.valueOf(value) }
            .getOrDefault(ChargingSource.UNKNOWN)
    }

    @TypeConverter
    fun fromChargingSpeedTier(tier: ChargingSpeedTier?): String? = tier?.name

    @TypeConverter
    fun toChargingSpeedTier(value: String?): ChargingSpeedTier? {
        return value?.let { ChargingSpeedTier.fromStoredName(it) }
    }

    @TypeConverter
    fun fromChargingPhase(phase: ChargingPhase): String = phase.name

    @TypeConverter
    fun toChargingPhase(value: String): ChargingPhase {
        return runCatching { ChargingPhase.valueOf(value) }
            .getOrDefault(ChargingPhase.UNKNOWN)
    }

    @TypeConverter
    fun fromChargingTerminationReason(reason: ChargingTerminationReason): String = reason.name

    @TypeConverter
    fun toChargingTerminationReason(value: String): ChargingTerminationReason {
        return runCatching { ChargingTerminationReason.valueOf(value) }
            .getOrDefault(ChargingTerminationReason.UNKNOWN)
    }
}