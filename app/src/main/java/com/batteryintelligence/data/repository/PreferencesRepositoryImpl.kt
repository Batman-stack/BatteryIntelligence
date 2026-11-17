package com.batteryintelligence.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : PreferencesRepository {

    private object PreferencesKeys {
        val SAMPLING_INTERVAL_SECONDS = intPreferencesKey("sampling_interval_seconds")
        val FOREGROUND_SERVICE_ENABLED = booleanPreferencesKey("foreground_service_enabled")
        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val HIGH_TEMP_ALERT_CELSIUS = floatPreferencesKey("high_temp_alert_celsius")
        val TARGET_CHARGE_ALERT_PERCENT = intPreferencesKey("target_charge_alert_percent")
        val LOW_BATTERY_ALERT_PERCENT = intPreferencesKey("low_battery_alert_percent")
        val DESIGN_CAPACITY_OVERRIDE = doublePreferencesKey("design_capacity_override")
    }

    override val preferences: Flow<MonitoringPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            MonitoringPreferences(
                samplingIntervalSeconds = prefs[PreferencesKeys.SAMPLING_INTERVAL_SECONDS] ?: 5,
                foregroundServiceEnabled = prefs[PreferencesKeys.FOREGROUND_SERVICE_ENABLED] ?: false,
                alertsEnabled = prefs[PreferencesKeys.ALERTS_ENABLED] ?: true,
                highTempAlertCelsius = prefs[PreferencesKeys.HIGH_TEMP_ALERT_CELSIUS] ?: 42.0f,
                targetChargeAlertPercent = prefs[PreferencesKeys.TARGET_CHARGE_ALERT_PERCENT] ?: 80,
                lowBatteryAlertPercent = prefs[PreferencesKeys.LOW_BATTERY_ALERT_PERCENT] ?: 20,
                userDesignCapacityMahOverride = prefs[PreferencesKeys.DESIGN_CAPACITY_OVERRIDE]
            )
        }

    override suspend fun updateSamplingInterval(seconds: Int) {
        dataStore.edit { it[PreferencesKeys.SAMPLING_INTERVAL_SECONDS] = seconds }
    }

    override suspend fun setForegroundServiceEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.FOREGROUND_SERVICE_ENABLED] = enabled }
    }

    override suspend fun setAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.ALERTS_ENABLED] = enabled }
    }

    override suspend fun updateHighTempAlert(celsius: Float?) {
        dataStore.edit {
            if (celsius != null) {
                it[PreferencesKeys.HIGH_TEMP_ALERT_CELSIUS] = celsius
            } else {
                it.remove(PreferencesKeys.HIGH_TEMP_ALERT_CELSIUS)
            }
        }
    }

    override suspend fun updateTargetChargeAlert(percent: Int?) {
        dataStore.edit {
            if (percent != null) {
                it[PreferencesKeys.TARGET_CHARGE_ALERT_PERCENT] = percent
            } else {
                it.remove(PreferencesKeys.TARGET_CHARGE_ALERT_PERCENT)
            }
        }
    }

    override suspend fun updateLowBatteryAlert(percent: Int?) {
        dataStore.edit {
            if (percent != null) {
                it[PreferencesKeys.LOW_BATTERY_ALERT_PERCENT] = percent
            } else {
                it.remove(PreferencesKeys.LOW_BATTERY_ALERT_PERCENT)
            }
        }
    }

    override suspend fun updateDesignCapacityOverride(capacityMah: Double?) {
        dataStore.edit {
            if (capacityMah != null) {
                it[PreferencesKeys.DESIGN_CAPACITY_OVERRIDE] = capacityMah
            } else {
                it.remove(PreferencesKeys.DESIGN_CAPACITY_OVERRIDE)
            }
        }
    }
}
