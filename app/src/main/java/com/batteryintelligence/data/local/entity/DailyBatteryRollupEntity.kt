package com.batteryintelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_battery_rollups")
data class DailyBatteryRollupEntity(
    @PrimaryKey
    @ColumnInfo(name = "date_iso")
    val dateIso: String,
    @ColumnInfo(name = "session_count")
    val sessionCount: Int,
    @ColumnInfo(name = "total_charging_duration_ms")
    val totalChargingDurationMs: Long,
    @ColumnInfo(name = "total_charge_added_mah")
    val totalChargeAddedMah: Double,
    @ColumnInfo(name = "total_energy_added_wh")
    val totalEnergyAddedWh: Double,
    @ColumnInfo(name = "avg_power_watts")
    val avgPowerWatts: Double?,
    @ColumnInfo(name = "max_power_watts")
    val maxPowerWatts: Double?,
    @ColumnInfo(name = "avg_temperature_celsius")
    val avgTemperatureCelsius: Float?,
    @ColumnInfo(name = "max_temperature_celsius")
    val maxTemperatureCelsius: Float?,
    @ColumnInfo(name = "total_discharge_percent")
    val totalDischargePercent: Int,
    @ColumnInfo(name = "avg_overnight_drain_pct_per_hr")
    val avgOvernightDrainPctPerHr: Float?,
    @ColumnInfo(name = "estimated_capacity_mah_avg")
    val estimatedCapacityMahAvg: Double?,
    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long
)
