package com.batteryintelligence.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.batteryintelligence.data.local.entity.ChargingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChargingSessionEntity): Long

    @Update
    suspend fun updateSession(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_sessions WHERE id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: Long): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE id = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: Long): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM charging_sessions WHERE is_active = 1 ORDER BY start_time_ms DESC LIMIT 1")
    suspend fun getActiveSession(): ChargingSessionEntity?

    @Query("SELECT * FROM charging_sessions WHERE is_active = 1 ORDER BY start_time_ms DESC LIMIT 1")
    fun observeActiveSession(): Flow<ChargingSessionEntity?>

    @Query("SELECT * FROM charging_sessions ORDER BY start_time_ms DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE is_active = 0 ORDER BY start_time_ms DESC LIMIT :limit")
    fun observeCompletedSessions(limit: Int): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE start_time_ms BETWEEN :startMs AND :endMs ORDER BY start_time_ms ASC")
    suspend fun getSessionsInWindow(startMs: Long, endMs: Long): List<ChargingSessionEntity>

    @Query(
        """
        SELECT * FROM charging_sessions
        WHERE is_active = 0 AND estimated_capacity_mah IS NOT NULL
        ORDER BY start_time_ms DESC
        LIMIT :limit
        """
    )
    suspend fun getCompletedSessionsWithCapacityEstimate(limit: Int = 20): List<ChargingSessionEntity>

    @Query(
        """
        SELECT * FROM charging_sessions
        WHERE is_active = 0 AND estimated_capacity_mah IS NOT NULL
        ORDER BY start_time_ms ASC
        LIMIT :limit
        """
    )
    fun observeSessionsWithCapacityEstimate(limit: Int = 20): Flow<List<ChargingSessionEntity>>

    @Query(
        """
        UPDATE charging_sessions
        SET is_active = 0,
            end_time_ms = COALESCE(end_time_ms, last_sample_time_ms),
            duration_ms = MAX(0, COALESCE(end_time_ms, last_sample_time_ms) - start_time_ms),
            termination_reason = CASE WHEN termination_reason = 'ACTIVE' THEN 'STALE_GAP_CLOSED' ELSE termination_reason END
        WHERE is_active = 1 AND id != :excludeId
        """
    )
    suspend fun closeOtherActiveSessions(excludeId: Long)

    @Query("SELECT * FROM charging_sessions WHERE is_active = 0 ORDER BY start_time_ms DESC LIMIT :limit")
    suspend fun getCompletedSessions(limit: Int = 200): List<ChargingSessionEntity>

    @Query("DELETE FROM charging_sessions WHERE is_active = 0 AND start_time_ms < :cutoffTimestampMs")
    suspend fun deleteOlderThan(cutoffTimestampMs: Long): Int
}