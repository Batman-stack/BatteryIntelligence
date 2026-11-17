package com.batteryintelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.batteryintelligence.domain.model.ChargingSource

@Entity(
    tableName = "charging_sessions",
    indices = [
        Index(value = ["start_time_ms"]),
        Index(value = ["is_active"])
    ]
)
data class ChargingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "start_time_ms")
    val startTimeMs: Long,
    @ColumnInfo(name = "end_time_ms")
    val endTimeMs: Long?,
    @ColumnInfo(name = "last_sample_time_ms")
    val lastSampleTimeMs: Long,
    @ColumnInfo(name = "start_level_percent")
    val startLevelPercent: Int,
    @ColumnInfo(name = "end_level_percent")
    val endLevelPercent: Int?,
    @ColumnInfo(name = "charging_source")
    val chargingSource: ChargingSource,
    @ColumnInfo(name = "start_charge_counter_uah")
    val startChargeCounterUah: Long?,
    @ColumnInfo(name = "end_charge_counter_uah")
    val endChargeCounterUah: Long?,
    @ColumnInfo(name = "start_energy_counter_nwh")
    val startEnergyCounterNwh: Long?,
    @ColumnInfo(name = "end_energy_counter_nwh")
    val endEnergyCounterNwh: Long?,
    @ColumnInfo(name = "integrated_charge_mah")
    val integratedChargeMah: Double,
    @ColumnInfo(name = "integrated_energy_wh")
    val integratedEnergyWh: Double,
    @ColumnInfo(name = "charge_added_uah")
    val chargeAddedUah: Long?,
    @ColumnInfo(name = "energy_added_wh")
    val energyAddedWh: Double?,
    @ColumnInfo(name = "charging_efficiency_percent")
    val chargingEfficiencyPercent: Double?,
    @ColumnInfo(name = "peak_current_ma")
    val peakCurrentMa: Double?,
    @ColumnInfo(name = "peak_power_watts")
    val peakPowerWatts: Double?,
    @ColumnInfo(name = "min_temperature_celsius")
    val minTemperatureCelsius: Float?,
    @ColumnInfo(name = "max_temperature_celsius")
    val maxTemperatureCelsius: Float?,
    @ColumnInfo(name = "temperature_sum_celsius")
    val temperatureSumCelsius: Double,
    @ColumnInfo(name = "temperature_sample_count")
    val temperatureSampleCount: Int,
    @ColumnInfo(name = "last_current_ma")
    val lastCurrentMa: Double?,
    @ColumnInfo(name = "last_power_watts")
    val lastPowerWatts: Double?,
    @ColumnInfo(name = "estimated_capacity_mah")
    val estimatedCapacityMah: Double?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean
)