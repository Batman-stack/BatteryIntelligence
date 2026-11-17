package com.batteryintelligence.data.local

import androidx.room.Database
import androidx.room.TypeConverters
import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.dao.OvernightLogDao
import com.batteryintelligence.data.local.entity.BatterySampleEntity
import com.batteryintelligence.data.local.entity.ChargingSessionEntity
import com.batteryintelligence.data.local.entity.OvernightLogEntity
import androidx.room.RoomDatabase

@Database(
    entities = [
        BatterySampleEntity::class,
        ChargingSessionEntity::class,
        OvernightLogEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(BatteryTypeConverters::class)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batterySampleDao(): BatterySampleDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun overnightLogDao(): OvernightLogDao

    companion object {
        const val DATABASE_NAME = "battery_intelligence.db"
    }
}