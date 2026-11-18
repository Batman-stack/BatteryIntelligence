package com.batteryintelligence.service

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.batteryintelligence.data.datasource.BatteryHardwareDataSource
import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.mapper.toSampleEntity
import com.batteryintelligence.data.session.ChargingSessionManager
import com.batteryintelligence.data.session.OvernightLogManager
import com.batteryintelligence.domain.intelligence.ChargingIntelligenceEngine
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.ChargingStatus
import com.batteryintelligence.domain.model.MonitoringPreferences
import com.batteryintelligence.domain.repository.PreferencesRepository
import com.batteryintelligence.widget.BatteryWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SmartAlarmConfig(
    val targetChargePercent: Int = 80,
    val highTempAlertCelsius: Float? = 42.0f,
    val thermalSlopeAlertCPerMin: Float? = 1.2f,
    val lowBatteryAlertPercent: Int? = 20,
    val alertsEnabled: Boolean = true
)

@AndroidEntryPoint
class BatteryMonitorService : LifecycleService() {

    @Inject lateinit var batteryDataSource: BatteryHardwareDataSource
    @Inject lateinit var batterySampleDao: BatterySampleDao
    @Inject lateinit var chargingSessionManager: ChargingSessionManager
    @Inject lateinit var overnightLogManager: OvernightLogManager
    @Inject lateinit var notificationHelper: BatteryNotificationHelper
    @Inject lateinit var preferencesRepository: PreferencesRepository
    @Inject lateinit var chargingIntelligenceEngine: ChargingIntelligenceEngine

    private var telemetryJob: Job? = null
    private var preferencesJob: Job? = null
    private var currentPreferences: MonitoringPreferences = MonitoringPreferences()
    private var alarmConfig: SmartAlarmConfig = SmartAlarmConfig()
    private var latestSnapshot: BatterySnapshot? = null
    private var latestIntelligence: ChargingIntelligenceState? = null

    private var targetAlarmFiredForCurrentCharge = false
    private var highTempAlarmFired = false
    private var thermalSlopeAlarmFired = false
    private var lowBatteryAlarmFired = false

    private var lastPersistedTimestampMs = 0L
    private var lastPersistedLevel: Int? = null
    private var lastPersistedSource: ChargingSource? = null
    private var lastPersistedStatus: ChargingStatus? = null
    private var lastWidgetLevel: Int? = null
    private var lastWidgetSource: ChargingSource? = null
    private var lastWidgetTier: ChargingSpeedTier? = null
    private var activeSamplingIntervalMs: Long = DEFAULT_SAMPLING_INTERVAL_MS

    private val powerManager: PowerManager? by lazy {
        getSystemService(PowerManager::class.java)
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        observePreferencesReactively()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP_MONITORING -> {
                promoteToForegroundImmediately()
                lifecycleScope.launch {
                    preferencesRepository.setForegroundServiceEnabled(false)
                    stopMonitoringAndShutdown()
                }
                return START_NOT_STICKY
            }

            ACTION_DISMISS_ALARM -> {
                notificationHelper.cancelTargetChargeAlarm()
                return START_REDELIVER_INTENT
            }

            ACTION_UPDATE_ALARM_CONFIG -> {
                applyAlarmExtras(intent)
                promoteToForegroundImmediately()
                latestSnapshot?.let { evaluateSmartAlarms(it, latestIntelligence) }
                notificationHelper.updateMonitoringNotification(
                    snapshot = latestSnapshot,
                    targetChargePercent = alarmConfig.targetChargePercent,
                    targetAlarmTriggered = targetAlarmFiredForCurrentCharge,
                    intelligenceState = latestIntelligence
                )
                ensureTelemetryCollectionRunning(activeSamplingIntervalMs)
                return START_REDELIVER_INTENT
            }

            else -> {
                applyAlarmExtras(intent)
                promoteToForegroundImmediately()
                ensureTelemetryCollectionRunning(activeSamplingIntervalMs)
                return START_REDELIVER_INTENT
            }
        }
    }

    private fun observePreferencesReactively() {
        if (preferencesJob?.isActive == true) return
        preferencesJob = lifecycleScope.launch {
            preferencesRepository.preferences.collect { prefs ->
                val prevTarget = alarmConfig.targetChargePercent
                currentPreferences = prefs
                val nextTarget = (prefs.targetChargeAlertPercent ?: 80)
                    .coerceIn(MIN_TARGET_CHARGE_PERCENT, MAX_TARGET_CHARGE_PERCENT)
                if (nextTarget > prevTarget) {
                    targetAlarmFiredForCurrentCharge = false
                }
                alarmConfig = SmartAlarmConfig(
                    targetChargePercent = nextTarget,
                    highTempAlertCelsius = prefs.highTempAlertCelsius,
                    thermalSlopeAlertCPerMin = prefs.thermalSlopeAlertCPerMin,
                    lowBatteryAlertPercent = prefs.lowBatteryAlertPercent,
                    alertsEnabled = prefs.alertsEnabled
                )
                val desiredIntervalMs = computeDesiredIntervalMs(prefs, latestSnapshot)
                if (desiredIntervalMs != activeSamplingIntervalMs) {
                    activeSamplingIntervalMs = desiredIntervalMs
                    restartTelemetryCollection(desiredIntervalMs)
                }
            }
        }
    }

    private fun computeDesiredIntervalMs(
        prefs: MonitoringPreferences,
        snapshot: BatterySnapshot?
    ): Long {
        val baseMs = (prefs.samplingIntervalSeconds.coerceIn(2, 30)) * 1000L
        val isChargingOrInteractive = snapshot?.isCharging != false ||
            (powerManager?.isInteractive == true)
        return if (isChargingOrInteractive) baseMs else STANDBY_RELAXED_INTERVAL_MS
    }

    private fun applyAlarmExtras(intent: Intent?) {
        if (intent == null) return

        val previousTarget = alarmConfig.targetChargePercent
        val updatedTarget = if (intent.hasExtra(EXTRA_TARGET_CHARGE_PERCENT)) {
            intent.getIntExtra(EXTRA_TARGET_CHARGE_PERCENT, previousTarget)
                .coerceIn(MIN_TARGET_CHARGE_PERCENT, MAX_TARGET_CHARGE_PERCENT)
        } else {
            previousTarget
        }

        val updatedHighTemp = if (intent.hasExtra(EXTRA_HIGH_TEMP_CELSIUS)) {
            val raw = intent.getFloatExtra(EXTRA_HIGH_TEMP_CELSIUS, -1f)
            if (raw > 0f) raw else null
        } else {
            alarmConfig.highTempAlertCelsius
        }

        val updatedAlertsEnabled = if (intent.hasExtra(EXTRA_ALERTS_ENABLED)) {
            intent.getBooleanExtra(EXTRA_ALERTS_ENABLED, alarmConfig.alertsEnabled)
        } else {
            alarmConfig.alertsEnabled
        }

        if (updatedTarget > previousTarget) {
            targetAlarmFiredForCurrentCharge = false
        }

        alarmConfig = alarmConfig.copy(
            targetChargePercent = updatedTarget,
            highTempAlertCelsius = updatedHighTemp,
            alertsEnabled = updatedAlertsEnabled
        )
    }

    private fun promoteToForegroundImmediately() {
        val notification = notificationHelper.buildMonitoringNotification(
            snapshot = latestSnapshot,
            targetChargePercent = alarmConfig.targetChargePercent,
            targetAlarmTriggered = targetAlarmFiredForCurrentCharge,
            intelligenceState = latestIntelligence
        )

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            BatteryNotificationHelper.MONITOR_NOTIFICATION_ID,
            notification,
            foregroundServiceType
        )
    }

    private fun restartTelemetryCollection(intervalMs: Long) {
        telemetryJob?.cancel()
        telemetryJob = null
        ensureTelemetryCollectionRunning(intervalMs)
    }

    private fun ensureTelemetryCollectionRunning(intervalMs: Long) {
        if (telemetryJob?.isActive == true) return

        telemetryJob = lifecycleScope.launch {
            batteryDataSource.observeBatteryChanges(samplingIntervalMs = intervalMs)
                .catch { /* Keep service resilient across transient IPC errors */ }
                .collect { snapshot ->
                    latestSnapshot = snapshot
                    val intelligence = chargingIntelligenceEngine.processSnapshot(
                        snapshot = snapshot,
                        preferences = currentPreferences,
                        effectiveCapacityMah = snapshot.designCapacityMah
                    )
                    latestIntelligence = intelligence

                    persistSampleIfNeeded(snapshot)
                    chargingSessionManager.processSnapshot(snapshot, intelligence)
                    overnightLogManager.evaluateOvernightWindow(snapshot.timestampMs)
                    evaluateSmartAlarms(snapshot, intelligence)

                    notificationHelper.updateMonitoringNotification(
                        snapshot = snapshot,
                        targetChargePercent = alarmConfig.targetChargePercent,
                        targetAlarmTriggered = targetAlarmFiredForCurrentCharge,
                        intelligenceState = intelligence
                    )

                    updateWidgetIfMeaningfulChange(snapshot, intelligence)

                    val adaptiveInterval = computeDesiredIntervalMs(currentPreferences, snapshot)
                    if (adaptiveInterval != activeSamplingIntervalMs) {
                        activeSamplingIntervalMs = adaptiveInterval
                        restartTelemetryCollection(adaptiveInterval)
                    }
                }
        }
    }

    private fun updateWidgetIfMeaningfulChange(
        snapshot: BatterySnapshot,
        intelligence: ChargingIntelligenceState
    ) {
        val changed = snapshot.levelPercent != lastWidgetLevel ||
            snapshot.chargingSource != lastWidgetSource ||
            intelligence.speedTier != lastWidgetTier
        if (changed) {
            lastWidgetLevel = snapshot.levelPercent
            lastWidgetSource = snapshot.chargingSource
            lastWidgetTier = intelligence.speedTier
            BatteryWidgetUpdater.updateAllWidgets(applicationContext)
        }
    }

    private suspend fun persistSampleIfNeeded(snapshot: BatterySnapshot) {
        val elapsedMs = snapshot.timestampMs - lastPersistedTimestampMs
        val stateChanged = snapshot.levelPercent != lastPersistedLevel ||
            snapshot.chargingSource != lastPersistedSource ||
            snapshot.chargingStatus != lastPersistedStatus

        if (lastPersistedTimestampMs == 0L || elapsedMs >= DB_PERSIST_INTERVAL_MS || stateChanged) {
            batterySampleDao.insertSample(snapshot.toSampleEntity())
            lastPersistedTimestampMs = snapshot.timestampMs
            lastPersistedLevel = snapshot.levelPercent
            lastPersistedSource = snapshot.chargingSource
            lastPersistedStatus = snapshot.chargingStatus
        }
    }

    private fun evaluateSmartAlarms(
        snapshot: BatterySnapshot,
        intelligence: ChargingIntelligenceState?
    ) {
        val isPlugged = snapshot.chargingSource.isPlugged ||
            snapshot.chargingStatus == ChargingStatus.CHARGING

        val level = snapshot.levelPercent
        val target = alarmConfig.targetChargePercent

        if (!isPlugged) {
            targetAlarmFiredForCurrentCharge = false
        } else if (level != null && level <= target - TARGET_HYSTERESIS_PERCENT) {
            targetAlarmFiredForCurrentCharge = false
        }

        if (alarmConfig.alertsEnabled &&
            isPlugged &&
            level != null &&
            level >= target &&
            !targetAlarmFiredForCurrentCharge
        ) {
            targetAlarmFiredForCurrentCharge = true
            notificationHelper.showTargetChargeAlarm(
                currentLevelPercent = level,
                targetChargePercent = target
            )
        }

        val tempThreshold = alarmConfig.highTempAlertCelsius
        val currentTemp = snapshot.temperatureCelsius
        if (tempThreshold != null && currentTemp != null) {
            if (currentTemp <= tempThreshold - TEMP_HYSTERESIS_CELSIUS) {
                highTempAlarmFired = false
            } else if (alarmConfig.alertsEnabled && currentTemp >= tempThreshold && !highTempAlarmFired) {
                highTempAlarmFired = true
                notificationHelper.showHighTemperatureAlert(
                    temperatureCelsius = currentTemp,
                    thresholdCelsius = tempThreshold
                )
            }
        }

        val slopeThreshold = alarmConfig.thermalSlopeAlertCPerMin
        val currentSlope = intelligence?.thermalSlopeCPerMin
        if (slopeThreshold != null && currentSlope != null) {
            if (currentSlope <= slopeThreshold * 0.6f) {
                thermalSlopeAlarmFired = false
            } else if (alarmConfig.alertsEnabled && currentSlope >= slopeThreshold && !thermalSlopeAlarmFired) {
                thermalSlopeAlarmFired = true
                notificationHelper.showThermalSlopeAlert(
                    slopeCPerMin = currentSlope,
                    thresholdCPerMin = slopeThreshold,
                    currentTempCelsius = currentTemp
                )
            }
        }

        val lowThreshold = alarmConfig.lowBatteryAlertPercent
        if (lowThreshold != null && level != null) {
            if (isPlugged || level >= lowThreshold + TARGET_HYSTERESIS_PERCENT) {
                lowBatteryAlarmFired = false
            } else if (alarmConfig.alertsEnabled && !isPlugged && level <= lowThreshold && !lowBatteryAlarmFired) {
                lowBatteryAlarmFired = true
                notificationHelper.showLowBatteryAlert(
                    currentLevelPercent = level,
                    thresholdPercent = lowThreshold
                )
            }
        }
    }

    private fun stopMonitoringAndShutdown() {
        telemetryJob?.cancel()
        telemetryJob = null
        preferencesJob?.cancel()
        preferencesJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        telemetryJob?.cancel()
        preferencesJob?.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START_MONITORING = "com.batteryintelligence.action.START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.batteryintelligence.action.STOP_MONITORING"
        const val ACTION_UPDATE_ALARM_CONFIG = "com.batteryintelligence.action.UPDATE_ALARM_CONFIG"
        const val ACTION_DISMISS_ALARM = "com.batteryintelligence.action.DISMISS_ALARM"

        const val EXTRA_TARGET_CHARGE_PERCENT = "extra_target_charge_percent"
        const val EXTRA_HIGH_TEMP_CELSIUS = "extra_high_temp_celsius"
        const val EXTRA_ALERTS_ENABLED = "extra_alerts_enabled"

        const val MIN_TARGET_CHARGE_PERCENT = 50
        const val MAX_TARGET_CHARGE_PERCENT = 100

        private const val DEFAULT_SAMPLING_INTERVAL_MS = 5_000L
        private const val STANDBY_RELAXED_INTERVAL_MS = 60_000L
        private const val DB_PERSIST_INTERVAL_MS = 60_000L
        private const val TARGET_HYSTERESIS_PERCENT = 3
        private const val TEMP_HYSTERESIS_CELSIUS = 2.0f

        fun start(
            context: Context,
            targetChargePercent: Int = 80,
            highTempAlertCelsius: Float? = 42.0f,
            alertsEnabled: Boolean = true
        ) {
            val intent = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
                putExtra(
                    EXTRA_TARGET_CHARGE_PERCENT,
                    targetChargePercent.coerceIn(MIN_TARGET_CHARGE_PERCENT, MAX_TARGET_CHARGE_PERCENT)
                )
                putExtra(EXTRA_HIGH_TEMP_CELSIUS, highTempAlertCelsius ?: -1f)
                putExtra(EXTRA_ALERTS_ENABLED, alertsEnabled)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun updateAlarmConfig(
            context: Context,
            targetChargePercent: Int,
            highTempAlertCelsius: Float?,
            alertsEnabled: Boolean
        ) {
            val intent = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_UPDATE_ALARM_CONFIG
                putExtra(
                    EXTRA_TARGET_CHARGE_PERCENT,
                    targetChargePercent.coerceIn(MIN_TARGET_CHARGE_PERCENT, MAX_TARGET_CHARGE_PERCENT)
                )
                putExtra(EXTRA_HIGH_TEMP_CELSIUS, highTempAlertCelsius ?: -1f)
                putExtra(EXTRA_ALERTS_ENABLED, alertsEnabled)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, BatteryMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.stopService(intent)
        }
    }
}