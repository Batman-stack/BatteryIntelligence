package com.batteryintelligence.data.local

import androidx.room.TypeConverter
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingStatus

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
}