package com.batteryintelligence.data.local

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.batteryintelligence.domain.model.ChargingPhase
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.ChargingTerminationReason
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatteryDatabaseMigrationTest {

    private val testDbName = "migration_test_battery_intelligence.db"

    @After
    fun tearDown() {
        ApplicationProvider.getApplicationContext<android.content.Context>().deleteDatabase(testDbName)
    }

    @Test
    fun migrate1To2_preservesLegacySessionsAndValidatesRoomSchema() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.deleteDatabase(testDbName)

        // 1. Seed a genuine V1 database schema + sample legacy session
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS battery_samples (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            timestamp_ms INTEGER NOT NULL,
                            level_percent INTEGER,
                            charging_status TEXT NOT NULL,
                            charging_source TEXT NOT NULL,
                            temperature_celsius REAL,
                            voltage_mv INTEGER,
                            current_now_ma REAL,
                            power_watts REAL,
                            charge_counter_uah INTEGER,
                            energy_counter_nwh INTEGER
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_samples_timestamp_ms ON battery_samples(timestamp_ms)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS charging_sessions (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            start_time_ms INTEGER NOT NULL,
                            end_time_ms INTEGER,
                            last_sample_time_ms INTEGER NOT NULL,
                            start_level_percent INTEGER NOT NULL,
                            end_level_percent INTEGER,
                            charging_source TEXT NOT NULL,
                            start_charge_counter_uah INTEGER,
                            end_charge_counter_uah INTEGER,
                            start_energy_counter_nwh INTEGER,
                            end_energy_counter_nwh INTEGER,
                            integrated_charge_mah REAL NOT NULL,
                            integrated_energy_wh REAL NOT NULL,
                            charge_added_uah INTEGER,
                            energy_added_wh REAL,
                            charging_efficiency_percent REAL,
                            peak_current_ma REAL,
                            peak_power_watts REAL,
                            min_temperature_celsius REAL,
                            max_temperature_celsius REAL,
                            temperature_sum_celsius REAL NOT NULL,
                            temperature_sample_count INTEGER NOT NULL,
                            last_current_ma REAL,
                            last_power_watts REAL,
                            estimated_capacity_mah REAL,
                            is_active INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_start_time_ms ON charging_sessions(start_time_ms)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_charging_sessions_is_active ON charging_sessions(is_active)")

                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS overnight_logs (
                            night_date_iso TEXT NOT NULL PRIMARY KEY,
                            start_time_ms INTEGER NOT NULL,
                            end_time_ms INTEGER NOT NULL,
                            duration_ms INTEGER NOT NULL,
                            start_level_percent INTEGER,
                            end_level_percent INTEGER,
                            battery_drop_percent INTEGER,
                            drain_rate_percent_per_hour REAL,
                            charge_drained_uah INTEGER,
                            min_temperature_celsius REAL,
                            max_temperature_celsius REAL,
                            was_plugged_in INTEGER NOT NULL,
                            sample_count INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_overnight_logs_start_time_ms ON overnight_logs(start_time_ms)")

                    db.execSQL(
                        """
                        INSERT INTO charging_sessions (
                            id, start_time_ms, end_time_ms, last_sample_time_ms,
                            start_level_percent, end_level_percent, charging_source,
                            integrated_charge_mah, integrated_energy_wh,
                            temperature_sum_celsius, temperature_sample_count, is_active
                        ) VALUES (
                            1, 1000, 61000, 61000,
                            20, 80, 'AC',
                            2400.0, 9.6,
                            64.0, 2, 0
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        FrameworkSQLiteOpenHelperFactory().create(config).use { helper ->
            helper.writableDatabase.close()
        }

        // 2. Open with Room V3 + MIGRATION_1_2 + MIGRATION_2_3 (Room verifies full V3 schema on open)
        val db = Room.databaseBuilder(context, BatteryDatabase::class.java, testDbName)
            .addMigrations(BatteryDatabase.MIGRATION_1_2, BatteryDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        try {
            val migrated = db.chargingSessionDao().getSessionById(1L)
            assertNotNull(migrated)
            assertEquals(60_000L, migrated!!.durationMs)
            assertEquals(32.0f, migrated.avgTemperatureCelsius!!, 0.01f)
            assertEquals(ChargingTerminationReason.USER_UNPLUGGED, migrated.terminationReason)

            // Verify V2 tables accept writes cleanly
            val sampleId = db.chargingSessionSampleDao().insertSample(
                com.batteryintelligence.data.local.entity.ChargingSessionSampleEntity(
                    sessionId = 1L,
                    timestampMs = 5_000L,
                    elapsedMs = 4_000L,
                    levelPercent = 25,
                    levelPrecisePercent = 25f,
                    voltageMv = 4200,
                    currentNowMa = 3000.0,
                    smoothedPowerWatts = 12.6,
                    temperatureCelsius = 31.5f,
                    thermalSlopeCPerMin = 0.2f,
                    speedTier = ChargingSpeedTier.NORMAL,
                    chargingPhase = ChargingPhase.CC,
                    chargeCounterUah = 1_250_000L
                )
            )
            assertEquals(1L, sampleId)

            // Verify V3 app_usage_sessions table accepts writes and queries cleanly
            db.appUsageSessionDao().insertSessions(
                listOf(
                    com.batteryintelligence.data.local.entity.AppUsageSessionEntity(
                        packageName = "com.example.reader",
                        appName = "Reader",
                        startTimeMs = 10_000L,
                        endTimeMs = 70_000L,
                        durationMs = 60_000L,
                        closeReason = "Backgrounded",
                        recordedAtMs = 75_000L
                    )
                )
            )
            val cached = db.appUsageSessionDao().getSessionsInWindow(0L, 100_000L)
            assertEquals(1, cached.size)
            assertEquals("com.example.reader", cached.first().packageName)
        } finally {
            db.close()
        }
    }
}
