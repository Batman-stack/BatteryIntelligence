package com.batteryintelligence.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.batteryintelligence.data.local.entity.OvernightLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OvernightLogDao {

    @Upsert
    suspend fun upsertLog(log: OvernightLogEntity)

    @Query("SELECT * FROM overnight_logs WHERE night_date_iso = :nightDateIso LIMIT 1")
    suspend fun getByNightDate(nightDateIso: String): OvernightLogEntity?

    @Query("SELECT * FROM overnight_logs ORDER BY start_time_ms DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int = 30): Flow<List<OvernightLogEntity>>

    @Query("SELECT * FROM overnight_logs ORDER BY start_time_ms DESC LIMIT 1")
    fun observeLatestLog(): Flow<OvernightLogEntity?>

    @Query("DELETE FROM overnight_logs WHERE start_time_ms < :cutoffTimestampMs")
    suspend fun deleteOlderThan(cutoffTimestampMs: Long): Int
}