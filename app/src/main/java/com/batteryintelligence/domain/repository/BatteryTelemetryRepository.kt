package com.batteryintelligence.domain.repository

import com.batteryintelligence.domain.model.BatterySampleRecord
import com.batteryintelligence.domain.model.BatterySnapshot
import kotlinx.coroutines.flow.Flow

interface BatteryTelemetryRepository {
    suspend fun getCurrentSnapshot(): BatterySnapshot

    fun observeLiveTelemetry(samplingIntervalMs: Long = 2_000L): Flow<BatterySnapshot>

    suspend fun recordSample(snapshot: BatterySnapshot)

    fun observeRecentSamples(sinceTimestampMs: Long): Flow<List<BatterySampleRecord>>

    suspend fun pruneSamplesOlderThan(cutoffTimestampMs: Long): Int
}