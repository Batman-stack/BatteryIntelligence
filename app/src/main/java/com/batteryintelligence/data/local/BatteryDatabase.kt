package com.batteryintelligence.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.batteryintelligence.data.local.dao.AppUsageSessionDao
import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.dao.ChargingSessionSampleDao
import com.batteryintelligence.data.local.dao.DailyBatteryRollupDao
import com.batteryintelligence.data.local.dao.OvernightLogDao
import com.batteryintelligence.data.local.entity.AppUsageSessionEntity
import com.batteryintelligence.data.local.entity.BatterySampleEntity
import com.batteryintelligence.data.local.entity.ChargingSessionEntity
import com.batteryintelligence.data.local.entity.ChargingSessionSampleEntity
import com.batteryintelligence.data.local.entity.DailyBatteryRollupEntity
import com.batteryintelligence.data.local.entity.OvernightLogEntity

@Database(
    entities = [
        BatterySampleEntity::class,
        ChargingSessionEntity::class,
        ChargingSessionSampleEntity::class,
        DailyBatteryRollupEntity::class,
        OvernightLogEntity::class,
        AppUsageSessionEntity::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(BatteryTypeConverters::class)
abstract class BatteryDatabase : RoomDatabase() {
    abstract fun batterySampleDao(): BatterySampleDao
    abstract fun chargingSessionDao(): ChargingSessionDao
    abstract fun chargingSessionSampleDao(): ChargingSessionSampleDao
    abstract fun dailyBatteryRollupDao(): DailyBatteryRollupDao
    abstract fun overnightLogDao(): OvernightLogDao
    abstract fun appUsageSessionDao(): AppUsageSessionDao

    companion object {
        const val DATABASE_NAME = "battery_intelligence.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN duration_ms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN current_sum_ma REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN power_sum_watts REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN telemetry_sample_count INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN avg_current_ma REAL")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN avg_power_watts REAL")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN avg_temperature_celsius REAL")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN peak_thermal_slope_c_per_min REAL")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN termination_reason TEXT NOT NULL DEFAULT 'ACTIVE'")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN dominant_speed_tier TEXT")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN cc_cv_transition_percent INTEGER")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN cc_cv_transition_time_ms INTEGER")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN initial_eta_100_ms INTEGER")
                db.execSQL("ALTER TABLE charging_sessions ADD COLUMN eta_error_100_ms INTEGER")

                db.execSQL(
                    """
                    UPDATE charging_sessions
                    SET duration_ms = MAX(0, COALESCE(end_time_ms, last_sample_time_ms) - start_time_ms),
                        avg_temperature_celsius = CASE
                            WHEN temperature_sample_count > 0 THEN temperature_sum_celsius / temperature_sample_count
                            ELSE NULL
                        END,
                        termination_reason = CASE
                            WHEN is_active = 1 THEN 'ACTIVE'
                            ELSE 'USER_UNPLUGGED'
                        END
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charging_sessions_charging_source_start_time_ms ON charging_sessions(charging_source, start_time_ms)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS charging_session_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        session_id INTEGER NOT NULL,
                        timestamp_ms INTEGER NOT NULL,
                        elapsed_ms INTEGER NOT NULL,
                        level_percent INTEGER,
                        level_precise_percent REAL,
                        voltage_mv INTEGER,
                        current_now_ma REAL,
                        smoothed_power_watts REAL,
                        temperature_celsius REAL,
                        thermal_slope_c_per_min REAL,
                        speed_tier TEXT NOT NULL,
                        charging_phase TEXT NOT NULL,
                        charge_counter_uah INTEGER,
                        FOREIGN KEY(session_id) REFERENCES charging_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_charging_session_samples_session_id_timestamp_ms ON charging_session_samples(session_id, timestamp_ms)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_battery_rollups (
                        date_iso TEXT NOT NULL PRIMARY KEY,
                        session_count INTEGER NOT NULL,
                        total_charging_duration_ms INTEGER NOT NULL,
                        total_charge_added_mah REAL NOT NULL,
                        total_energy_added_wh REAL NOT NULL,
                        avg_power_watts REAL,
                        max_power_watts REAL,
                        avg_temperature_celsius REAL,
                        max_temperature_celsius REAL,
                        total_discharge_percent INTEGER NOT NULL,
                        avg_overnight_drain_pct_per_hr REAL,
                        estimated_capacity_mah_avg REAL,
                        updated_at_ms INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_usage_sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        package_name TEXT NOT NULL,
                        app_name TEXT NOT NULL,
                        start_time_ms INTEGER NOT NULL,
                        end_time_ms INTEGER NOT NULL,
                        duration_ms INTEGER NOT NULL,
                        close_reason TEXT NOT NULL,
                        recorded_at_ms INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_usage_sessions_start_time_ms ON app_usage_sessions(start_time_ms)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_app_usage_sessions_package_name_start_time_ms ON app_usage_sessions(package_name, start_time_ms)"
                )
            }
        }
    }
}