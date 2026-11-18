package com.batteryintelligence.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.batteryintelligence.domain.model.AppThemeMode
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.model.SpeedTierThresholds
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
        val THERMAL_SLOPE_ALERT_C_PER_MIN = floatPreferencesKey("thermal_slope_alert_c_per_min")
        val TARGET_CHARGE_ALERT_PERCENT = intPreferencesKey("target_charge_alert_percent")
        val LOW_BATTERY_ALERT_PERCENT = intPreferencesKey("low_battery_alert_percent")
        val DESIGN_CAPACITY_OVERRIDE = doublePreferencesKey("design_capacity_override")
        val NORMAL_MIN_WATTS = doublePreferencesKey("speed_normal_min_watts")
        val FAST_MIN_WATTS = doublePreferencesKey("speed_fast_min_watts")
        val VERY_FAST_MIN_WATTS = doublePreferencesKey("speed_very_fast_min_watts")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
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
            val themeMode = prefs[PreferencesKeys.THEME_MODE]?.let { raw ->
                runCatching { AppThemeMode.valueOf(raw) }.getOrDefault(AppThemeMode.LIGHT)
            } ?: AppThemeMode.LIGHT

            val normalMin = prefs[PreferencesKeys.NORMAL_MIN_WATTS] ?: 7.5
            val fastMin = prefs[PreferencesKeys.FAST_MIN_WATTS] ?: 18.0
            val veryFastMin = prefs[PreferencesKeys.VERY_FAST_MIN_WATTS] ?: 35.0

            MonitoringPreferences(
                samplingIntervalSeconds = (prefs[PreferencesKeys.SAMPLING_INTERVAL_SECONDS] ?: 5).coerceIn(2, 30),
                foregroundServiceEnabled = prefs[PreferencesKeys.FOREGROUND_SERVICE_ENABLED] ?: false,
                alertsEnabled = prefs[PreferencesKeys.ALERTS_ENABLED] ?: true,
                highTempAlertCelsius = prefs[PreferencesKeys.HIGH_TEMP_ALERT_CELSIUS] ?: 42.0f,
                thermalSlopeAlertCPerMin = prefs[PreferencesKeys.THERMAL_SLOPE_ALERT_C_PER_MIN] ?: 1.2f,
                targetChargeAlertPercent = (prefs[PreferencesKeys.TARGET_CHARGE_ALERT_PERCENT] ?: 80).coerceIn(50, 100),
                lowBatteryAlertPercent = (prefs[PreferencesKeys.LOW_BATTERY_ALERT_PERCENT] ?: 20).coerceIn(5, 45),
                userDesignCapacityMahOverride = prefs[PreferencesKeys.DESIGN_CAPACITY_OVERRIDE],
                speedTierThresholds = SpeedTierThresholds(
                    normalMinWatts = normalMin,
                    fastMinWatts = fastMin.coerceAtLeast(normalMin + 2.0),
                    veryFastMinWatts = veryFastMin.coerceAtLeast(fastMin + 4.0)
                ),
                themeMode = themeMode,
                useDynamicColor = prefs[PreferencesKeys.USE_DYNAMIC_COLOR] ?: false,
                hapticsEnabled = prefs[PreferencesKeys.HAPTICS_ENABLED] ?: true
            )
        }

    override suspend fun updateSamplingInterval(seconds: Int) {
        dataStore.edit { it[PreferencesKeys.SAMPLING_INTERVAL_SECONDS] = seconds.coerceIn(2, 30) }
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
                it[PreferencesKeys.HIGH_TEMP_ALERT_CELSIUS] = celsius.coerceIn(32.0f, 55.0f)
            } else {
                it.remove(PreferencesKeys.HIGH_TEMP_ALERT_CELSIUS)
            }
        }
    }

    override suspend fun updateThermalSlopeAlert(cPerMin: Float?) {
        dataStore.edit {
            if (cPerMin != null) {
                it[PreferencesKeys.THERMAL_SLOPE_ALERT_C_PER_MIN] = cPerMin.coerceIn(0.3f, 5.0f)
            } else {
                it.remove(PreferencesKeys.THERMAL_SLOPE_ALERT_C_PER_MIN)
            }
        }
    }

    override suspend fun updateTargetChargeAlert(percent: Int?) {
        dataStore.edit {
            if (percent != null) {
                it[PreferencesKeys.TARGET_CHARGE_ALERT_PERCENT] = percent.coerceIn(50, 100)
            } else {
                it.remove(PreferencesKeys.TARGET_CHARGE_ALERT_PERCENT)
            }
        }
    }

    override suspend fun updateLowBatteryAlert(percent: Int?) {
        dataStore.edit {
            if (percent != null) {
                it[PreferencesKeys.LOW_BATTERY_ALERT_PERCENT] = percent.coerceIn(5, 45)
            } else {
                it.remove(PreferencesKeys.LOW_BATTERY_ALERT_PERCENT)
            }
        }
    }

    override suspend fun updateDesignCapacityOverride(capacityMah: Double?) {
        dataStore.edit {
            if (capacityMah != null && capacityMah > 100.0) {
                it[PreferencesKeys.DESIGN_CAPACITY_OVERRIDE] = capacityMah
            } else {
                it.remove(PreferencesKeys.DESIGN_CAPACITY_OVERRIDE)
            }
        }
    }

    override suspend fun updateSpeedTierThresholds(thresholds: SpeedTierThresholds) {
        val safeNormal = thresholds.normalMinWatts.coerceIn(3.0, 15.0)
        val safeFast = thresholds.fastMinWatts.coerceIn(safeNormal + 2.0, 30.0)
        val safeVeryFast = thresholds.veryFastMinWatts.coerceIn(safeFast + 4.0, 80.0)
        dataStore.edit {
            it[PreferencesKeys.NORMAL_MIN_WATTS] = safeNormal
            it[PreferencesKeys.FAST_MIN_WATTS] = safeFast
            it[PreferencesKeys.VERY_FAST_MIN_WATTS] = safeVeryFast
        }
    }

    override suspend fun updateThemeMode(themeMode: AppThemeMode) {
        dataStore.edit { it[PreferencesKeys.THEME_MODE] = themeMode.name }
    }

    override suspend fun setUseDynamicColor(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.USE_DYNAMIC_COLOR] = enabled }
    }

    override suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.HAPTICS_ENABLED] = enabled }
    }
}
