package com.batteryintelligence.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.batteryintelligence.data.local.entity.AppUsageSessionEntity

@Dao
interface AppUsageSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<AppUsageSessionEntity>)

    @Query("SELECT * FROM app_usage_sessions WHERE end_time_ms >= :windowStartMs AND start_time_ms <= :windowEndMs ORDER BY start_time_ms ASC")
    suspend fun getSessionsInWindow(windowStartMs: Long, windowEndMs: Long): List<AppUsageSessionEntity>

    @Query("DELETE FROM app_usage_sessions WHERE start_time_ms >= :windowStartMs")
    suspend fun deleteFromWindow(windowStartMs: Long): Int

    @Query("DELETE FROM app_usage_sessions WHERE end_time_ms < :cutoffTimestampMs")
    suspend fun deleteOlderThan(cutoffTimestampMs: Long): Int
}
