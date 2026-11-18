package com.batteryintelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.batteryintelligence.domain.model.ChargingPhase
import com.batteryintelligence.domain.model.ChargingSpeedTier

@Entity(
    tableName = "charging_session_samples",
    foreignKeys = [
        ForeignKey(
            entity = ChargingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id", "timestamp_ms"])
    ]
)
data class ChargingSessionSampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "session_id")
    val sessionId: Long,
    @ColumnInfo(name = "timestamp_ms")
    val timestampMs: Long,
    @ColumnInfo(name = "elapsed_ms")
    val elapsedMs: Long,
    @ColumnInfo(name = "level_percent")
    val levelPercent: Int?,
    @ColumnInfo(name = "level_precise_percent")
    val levelPrecisePercent: Float?,
    @ColumnInfo(name = "voltage_mv")
    val voltageMv: Int?,
    @ColumnInfo(name = "current_now_ma")
    val currentNowMa: Double?,
    @ColumnInfo(name = "smoothed_power_watts")
    val smoothedPowerWatts: Double?,
    @ColumnInfo(name = "temperature_celsius")
    val temperatureCelsius: Float?,
    @ColumnInfo(name = "thermal_slope_c_per_min")
    val thermalSlopeCPerMin: Float?,
    @ColumnInfo(name = "speed_tier")
    val speedTier: ChargingSpeedTier,
    @ColumnInfo(name = "charging_phase")
    val chargingPhase: ChargingPhase,
    @ColumnInfo(name = "charge_counter_uah")
    val chargeCounterUah: Long?
)
