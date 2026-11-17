package com.batteryintelligence.data.datasource

import com.batteryintelligence.domain.model.BatterySnapshot
import kotlinx.coroutines.flow.Flow

interface BatteryHardwareDataSource {
    suspend fun readSnapshot(): BatterySnapshot

    fun observeBatteryChanges(samplingIntervalMs: Long = 2_000L): Flow<BatterySnapshot>
}