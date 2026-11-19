package com.batteryintelligence.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.batteryintelligence.data.datasource.AndroidBatteryDataSource
import com.batteryintelligence.data.datasource.BatteryHardwareDataSource
import com.batteryintelligence.data.local.BatteryDatabase
import com.batteryintelligence.data.local.dao.AppUsageSessionDao
import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.dao.ChargingSessionSampleDao
import com.batteryintelligence.data.local.dao.DailyBatteryRollupDao
import com.batteryintelligence.data.local.dao.OvernightLogDao
import com.batteryintelligence.data.repository.AppUsageRepositoryImpl
import com.batteryintelligence.data.repository.BatteryTelemetryRepositoryImpl
import com.batteryintelligence.data.repository.ChargingSessionRepositoryImpl
import com.batteryintelligence.data.repository.DeviceIntelligenceRepositoryImpl
import com.batteryintelligence.data.repository.OvernightLogRepositoryImpl
import com.batteryintelligence.data.repository.PreferencesRepositoryImpl
import com.batteryintelligence.domain.repository.AppUsageRepository
import com.batteryintelligence.domain.repository.BatteryTelemetryRepository
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.DeviceIntelligenceRepository
import com.batteryintelligence.domain.repository.OvernightLogRepository
import com.batteryintelligence.domain.repository.PreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindBatteryHardwareDataSource(
        impl: AndroidBatteryDataSource
    ): BatteryHardwareDataSource

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        impl: PreferencesRepositoryImpl
    ): PreferencesRepository

    @Binds
    @Singleton
    abstract fun bindBatteryTelemetryRepository(
        impl: BatteryTelemetryRepositoryImpl
    ): BatteryTelemetryRepository

    @Binds
    @Singleton
    abstract fun bindChargingSessionRepository(
        impl: ChargingSessionRepositoryImpl
    ): ChargingSessionRepository

    @Binds
    @Singleton
    abstract fun bindAppUsageRepository(
        impl: AppUsageRepositoryImpl
    ): AppUsageRepository

    @Binds
    @Singleton
    abstract fun bindOvernightLogRepository(
        impl: OvernightLogRepositoryImpl
    ): OvernightLogRepository

    @Binds
    @Singleton
    abstract fun bindDeviceIntelligenceRepository(
        impl: DeviceIntelligenceRepositoryImpl
    ): DeviceIntelligenceRepository

    companion object {
        @Provides
        @JvmStatic
        @Singleton
        fun provideBatteryDatabase(
            @ApplicationContext context: Context
        ): BatteryDatabase {
            return Room.databaseBuilder(
                context,
                BatteryDatabase::class.java,
                BatteryDatabase.DATABASE_NAME
            )
                .addMigrations(
                    BatteryDatabase.MIGRATION_1_2,
                    BatteryDatabase.MIGRATION_2_3
                )
                .build()
        }

        @Provides
        @JvmStatic
        fun provideBatterySampleDao(database: BatteryDatabase): BatterySampleDao {
            return database.batterySampleDao()
        }

        @Provides
        @JvmStatic
        fun provideChargingSessionDao(database: BatteryDatabase): ChargingSessionDao {
            return database.chargingSessionDao()
        }

        @Provides
        @JvmStatic
        fun provideChargingSessionSampleDao(database: BatteryDatabase): ChargingSessionSampleDao {
            return database.chargingSessionSampleDao()
        }

        @Provides
        @JvmStatic
        fun provideDailyBatteryRollupDao(database: BatteryDatabase): DailyBatteryRollupDao {
            return database.dailyBatteryRollupDao()
        }

        @Provides
        @JvmStatic
        fun provideOvernightLogDao(database: BatteryDatabase): OvernightLogDao {
            return database.overnightLogDao()
        }

        @Provides
        @JvmStatic
        fun provideAppUsageSessionDao(database: BatteryDatabase): AppUsageSessionDao {
            return database.appUsageSessionDao()
        }

        @Provides
        @JvmStatic
        @Singleton
        fun providePreferencesDataStore(
            @ApplicationContext context: Context
        ): DataStore<Preferences> {
            return PreferenceDataStoreFactory.create(
                produceFile = { context.preferencesDataStoreFile("battery_intelligence_prefs") }
            )
        }
    }
}