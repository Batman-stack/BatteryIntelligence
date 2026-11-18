package com.batteryintelligence.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.batteryintelligence.data.local.entity.ChargingSessionSampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChargingSessionSampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: ChargingSessionSampleEntity): Long

    @Query("SELECT * FROM charging_session_samples WHERE session_id = :sessionId ORDER BY timestamp_ms ASC")
    fun observeSamplesForSession(sessionId: Long): Flow<List<ChargingSessionSampleEntity>>

    @Query("SELECT * FROM charging_session_samples WHERE session_id = :sessionId ORDER BY timestamp_ms ASC")
    suspend fun getSamplesForSession(sessionId: Long): List<ChargingSessionSampleEntity>

    @Query("SELECT * FROM charging_session_samples WHERE session_id = :sessionId ORDER BY timestamp_ms DESC LIMIT 1")
    suspend fun getLatestSampleForSession(sessionId: Long): ChargingSessionSampleEntity?

    @Query("DELETE FROM charging_session_samples WHERE timestamp_ms < :cutoffTimestampMs")
    suspend fun deleteOlderThan(cutoffTimestampMs: Long): Int

    @Query(
        """
        DELETE FROM charging_session_samples
        WHERE session_id NOT IN (
            SELECT id FROM charging_sessions ORDER BY start_time_ms DESC LIMIT :keepSessionCount
        )
        """
    )
    suspend fun pruneBeyondRecentSessions(keepSessionCount: Int = 100): Int
}
