package com.batteryintelligence.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object BatteryWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAllWidgets(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                BatteryStatusGlanceWidget().updateAll(appContext)
            }
        }
    }
}
