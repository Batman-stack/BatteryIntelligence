package com.batteryintelligence.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.ChargingTerminationReason

@Entity(
    tableName = "charging_sessions",
    indices = [
        Index(value = ["start_time_ms"]),
        Index(value = ["is_active"]),
        Index(value = ["charging_source", "start_time_ms"])
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
    val isActive: Boolean,
    @ColumnInfo(name = "duration_ms", defaultValue = "0")
    val durationMs: Long = 0L,
    @ColumnInfo(name = "current_sum_ma", defaultValue = "0.0")
    val currentSumMa: Double = 0.0,
    @ColumnInfo(name = "power_sum_watts", defaultValue = "0.0")
    val powerSumWatts: Double = 0.0,
    @ColumnInfo(name = "telemetry_sample_count", defaultValue = "0")
    val telemetrySampleCount: Int = 0,
    @ColumnInfo(name = "avg_current_ma")
    val avgCurrentMa: Double? = null,
    @ColumnInfo(name = "avg_power_watts")
    val avgPowerWatts: Double? = null,
    @ColumnInfo(name = "avg_temperature_celsius")
    val avgTemperatureCelsius: Float? = null,
    @ColumnInfo(name = "peak_thermal_slope_c_per_min")
    val peakThermalSlopeCPerMin: Float? = null,
    @ColumnInfo(name = "termination_reason", defaultValue = "'ACTIVE'")
    val terminationReason: ChargingTerminationReason = ChargingTerminationReason.ACTIVE,
    @ColumnInfo(name = "dominant_speed_tier")
    val dominantSpeedTier: ChargingSpeedTier? = null,
    @ColumnInfo(name = "cc_cv_transition_percent")
    val ccCvTransitionPercent: Int? = null,
    @ColumnInfo(name = "cc_cv_transition_time_ms")
    val ccCvTransitionTimeMs: Long? = null,
    @ColumnInfo(name = "initial_eta_100_ms")
    val initialEta100Ms: Long? = null,
    @ColumnInfo(name = "eta_error_100_ms")
    val etaError100Ms: Long? = null
)