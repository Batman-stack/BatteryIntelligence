package com.batteryintelligence.domain.repository

import com.batteryintelligence.domain.model.OvernightLog
import kotlinx.coroutines.flow.Flow

interface OvernightLogRepository {
    fun observeRecentLogs(limit: Int = 30): Flow<List<OvernightLog>>

    fun observeLatestLog(): Flow<OvernightLog?>

    suspend fun getLogForNight(nightDateIso: String): OvernightLog?

    suspend fun evaluateOvernightWindow(referenceTimestampMs: Long)

    suspend fun pruneLogsOlderThan(cutoffTimestampMs: Long): Int
}
