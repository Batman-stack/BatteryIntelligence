package com.batteryintelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingStatus

@Entity(
    tableName = "battery_samples",
    indices = [Index(value = ["timestamp_ms"])]
)
data class BatterySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    @ColumnInfo(name = "level_percent")
    val levelPercent: Int?,
    @ColumnInfo(name = "charging_status")
    val chargingStatus: ChargingStatus,
    @ColumnInfo(name = "charging_source")
    val chargingSource: ChargingSource,
    @ColumnInfo(name = "temperature_celsius")
    val temperatureCelsius: Float?,
    @ColumnInfo(name = "voltage_mv")
    val voltageMv: Int?,
    @ColumnInfo(name = "current_now_ma")
    val currentNowMa: Double?,
    @ColumnInfo(name = "power_watts")
    val powerWatts: Double?,
    @ColumnInfo(name = "charge_counter_uah")
    val chargeCounterUah: Long?,
    @ColumnInfo(name = "energy_counter_nwh")
    val energyCounterNwh: Long?
)