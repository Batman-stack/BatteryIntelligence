package com.batteryintelligence.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.batteryintelligence.domain.model.BatterySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager: NotificationManagerCompat by lazy {
        NotificationManagerCompat.from(context)
    }

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val monitorChannel = NotificationChannel(
            CHANNEL_MONITOR_ID,
            "Live Battery Monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing battery telemetry and active charging alarm target."
            setShowBadge(false)
        }

        val alarmChannel = NotificationChannel(
            CHANNEL_SMART_ALARM_ID,
            "Smart Charging & Thermal Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when battery reaches your 80%–100% target or temperature limit."
            enableVibration(true)
        }

        manager.createNotificationChannel(monitorChannel)
        manager.createNotificationChannel(alarmChannel)
    }

    fun buildMonitoringNotification(
        snapshot: BatterySnapshot?,
        targetChargePercent: Int,
        targetAlarmTriggered: Boolean
    ): Notification {
        val levelText = snapshot?.levelPercent?.let { "$it%" } ?: "Not Available"
        val currentText = snapshot?.currentNowMa?.let {
            String.format(Locale.US, "%+.0f mA", it)
        } ?: "Current: Not Available"
        val powerText = snapshot?.powerWatts?.let {
            String.format(Locale.US, "%.1f W", it)
        } ?: "Power: Not Available"
        val tempText = snapshot?.temperatureCelsius?.let {
            String.format(Locale.US, "%.1f°C", it)
        } ?: "Temp: Not Available"

        val statusTitle = when {
            snapshot == null -> "Battery Intelligence Active"
            targetAlarmTriggered && snapshot.chargingSource.isPlugged ->
                "Target $targetChargePercent% Reached • $levelText"
            snapshot.chargingSource.isPlugged ->
                "Charging $levelText • Target $targetChargePercent%"
            else ->
                "Monitoring $levelText • Target $targetChargePercent%"
        }

        val contentLine = "$currentText • $powerText • $tempText"

        val stopIntent = Intent(context, BatteryMonitorService::class.java).apply {
            action = BatteryMonitorService.ACTION_STOP_MONITORING
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            REQUEST_STOP_SERVICE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITOR_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle(statusTitle)
            .setContentText(contentLine)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(createLaunchPendingIntent())
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    fun showTargetChargeAlarm(
        currentLevelPercent: Int,
        targetChargePercent: Int
    ) {
        if (!hasPostNotificationsPermission()) return

        val dismissIntent = Intent(context, BatteryMonitorService::class.java).apply {
            action = BatteryMonitorService.ACTION_DISMISS_ALARM
        }
        val dismissPendingIntent = PendingIntent.getService(
            context,
            REQUEST_DISMISS_ALARM,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SMART_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Smart Charging Target Reached ($currentLevelPercent%)")
            .setContentText(
                "Battery reached your $targetChargePercent% limit. Unplug charger to reduce cell stress."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(createLaunchPendingIntent())
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                dismissPendingIntent
            )
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(TARGET_ALARM_NOTIFICATION_ID, notification)
        }
    }

    fun showHighTemperatureAlert(
        temperatureCelsius: Float,
        thresholdCelsius: Float
    ) {
        if (!hasPostNotificationsPermission()) return

        val formattedTemp = String.format(Locale.US, "%.1f°C", temperatureCelsius)
        val formattedLimit = String.format(Locale.US, "%.1f°C", thresholdCelsius)

        val notification = NotificationCompat.Builder(context, CHANNEL_SMART_ALARM_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("High Battery Temperature ($formattedTemp)")
            .setContentText(
                "Battery temperature exceeded your $formattedLimit threshold. Consider pausing fast charging."
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(createLaunchPendingIntent())
            .build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(TEMP_ALARM_NOTIFICATION_ID, notification)
        }
    }

    fun cancelTargetChargeAlarm() {
        notificationManager.cancel(TARGET_ALARM_NOTIFICATION_ID)
    }

    fun updateMonitoringNotification(
        snapshot: BatterySnapshot?,
        targetChargePercent: Int,
        targetAlarmTriggered: Boolean
    ) {
        if (!hasPostNotificationsPermission()) return

        val notification = buildMonitoringNotification(
            snapshot = snapshot,
            targetChargePercent = targetChargePercent,
            targetAlarmTriggered = targetAlarmTriggered
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(MONITOR_NOTIFICATION_ID, notification)
        }
    }

    private fun createLaunchPendingIntent(): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            } ?: return null

        return PendingIntent.getActivity(
            context,
            REQUEST_LAUNCH_APP,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun hasPostNotificationsPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_MONITOR_ID = "battery_monitor_channel"
        const val CHANNEL_SMART_ALARM_ID = "smart_charging_alarm_channel"

        const val MONITOR_NOTIFICATION_ID = 1001
        const val TARGET_ALARM_NOTIFICATION_ID = 1002
        const val TEMP_ALARM_NOTIFICATION_ID = 1003

        private const val REQUEST_LAUNCH_APP = 2001
        private const val REQUEST_STOP_SERVICE = 2002
        private const val REQUEST_DISMISS_ALARM = 2003
    }
}