package com.batteryintelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "overnight_logs",
    indices = [Index(value = ["start_time_ms"])]
)
data class OvernightLogEntity(
    @PrimaryKey
    @ColumnInfo(name = "night_date_iso")
    val nightDateIso: String,
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,
    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "start_level_percent")
    val startLevelPercent: Int?,
    @ColumnInfo(name = "end_level_percent")
    val endLevelPercent: Int?,
    @ColumnInfo(name = "battery_drop_percent")
    val batteryDropPercent: Int?,
    @ColumnInfo(name = "drain_rate_percent_per_hour")
    val drainRatePercentPerHour: Float?,
    @ColumnInfo(name = "charge_drained_uah")
    val chargeDrainedUah: Long?,
    @ColumnInfo(name = "min_temperature_celsius")
    val minTemperatureCelsius: Float?,
    @ColumnInfo(name = "max_temperature_celsius")
    val maxTemperatureCelsius: Float?,
    @ColumnInfo(name = "was_plugged_in")
    val wasPluggedIn: Boolean,
    @ColumnInfo(name = "sample_count")
    val sampleCount: Int
)