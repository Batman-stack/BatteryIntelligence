package com.batteryintelligence.receiver

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkManager
import com.batteryintelligence.domain.repository.PreferencesRepository
import com.batteryintelligence.service.BatteryMonitorService
import com.batteryintelligence.widget.BatteryWidgetUpdater
import com.batteryintelligence.worker.BatteryTelemetryWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val workManager = WorkManager.getInstance(appContext)
                BatteryTelemetryWorker.schedulePeriodicLogging(workManager)
                BatteryTelemetryWorker.enqueueImmediateSnapshot(workManager)

                val prefs = preferencesRepository.preferences.first()
                if (prefs.foregroundServiceEnabled) {
                    try {
                        BatteryMonitorService.start(
                            context = appContext,
                            targetChargePercent = prefs.targetChargeAlertPercent ?: 80,
                            highTempAlertCelsius = prefs.highTempAlertCelsius,
                            alertsEnabled = prefs.alertsEnabled
                        )
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            e !is ForegroundServiceStartNotAllowedException
                        ) {
                            // Ignore transient boot startup failures; WorkManager remains scheduled
                        }
                    }
                }

                BatteryWidgetUpdater.updateAllWidgets(appContext)
            } finally {
                pendingResult.finish()
            }
        }
    }
}