package com.batteryintelligence.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.batteryintelligence.data.local.entity.DailyBatteryRollupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyBatteryRollupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRollup(rollup: DailyBatteryRollupEntity)

    @Query("SELECT * FROM daily_battery_rollups WHERE date_iso = :dateIso LIMIT 1")
    suspend fun getByDate(dateIso: String): DailyBatteryRollupEntity?

    @Query("SELECT * FROM daily_battery_rollups WHERE date_iso >= :sinceIso ORDER BY date_iso ASC")
    fun observeRollupsSince(sinceIso: String): Flow<List<DailyBatteryRollupEntity>>

    @Query("SELECT * FROM daily_battery_rollups ORDER BY date_iso DESC LIMIT :limit")
    fun observeRecentRollups(limit: Int): Flow<List<DailyBatteryRollupEntity>>
}
