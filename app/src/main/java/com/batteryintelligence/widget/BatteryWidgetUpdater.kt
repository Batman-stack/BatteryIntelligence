package com.batteryintelligence.widget

import android.content.Context
import android.content.Intent

object BatteryWidgetUpdater {
    const val ACTION_REFRESH_WIDGETS = "com.batteryintelligence.widget.ACTION_REFRESH_WIDGETS"

    fun updateAllWidgets(context: Context) {
        val intent = Intent(ACTION_REFRESH_WIDGETS).apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(intent)
    }
}
