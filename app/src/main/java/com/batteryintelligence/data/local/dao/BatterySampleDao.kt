package com.batteryintelligence.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.batteryintelligence.data.local.entity.BatterySampleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BatterySampleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSample(sample: BatterySampleEntity): Long

    @Query("SELECT * FROM battery_samples WHERE timestamp_ms >= :sinceTimestampMs ORDER BY timestamp_ms ASC")
    fun observeSamplesSince(sinceTimestampMs: Long): Flow<List<BatterySampleEntity>>

    @Query("SELECT * FROM battery_samples WHERE timestamp_ms BETWEEN :startMs AND :endMs ORDER BY timestamp_ms ASC")
    suspend fun getSamplesBetween(startMs: Long, endMs: Long): List<BatterySampleEntity>

    @Query("SELECT * FROM battery_samples ORDER BY timestamp_ms DESC LIMIT 1")
    suspend fun getLatestSample(): BatterySampleEntity?

    @Query("DELETE FROM battery_samples WHERE timestamp_ms < :cutoffTimestampMs")
    suspend fun deleteOlderThan(cutoffTimestampMs: Long): Int
}