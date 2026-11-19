package com.batteryintelligence.ui.components

import com.batteryintelligence.domain.model.CapacityUnit
import com.batteryintelligence.domain.model.TemperatureUnit
import java.util.Locale

object UnitFormatters {

    fun formatTemperature(celsius: Float?, unit: TemperatureUnit): String {
        if (celsius == null) return "-- ${unit.symbol}"
        return when (unit) {
            TemperatureUnit.CELSIUS -> String.format(Locale.US, "%.1f °C", celsius)
            TemperatureUnit.FAHRENHEIT -> {
                val f = (celsius * 9f / 5f) + 32f
                String.format(Locale.US, "%.1f °F", f)
            }
        }
    }

    fun formatCapacity(mah: Double?, unit: CapacityUnit): String {
        if (mah == null) return "--"
        return when (unit) {
            CapacityUnit.MAH -> String.format(Locale.US, "%.0f mAh", mah)
            CapacityUnit.AH -> String.format(Locale.US, "%.2f Ah", mah / 1000.0)
        }
    }

    fun formatDurationCompact(durationMs: Long): String {
        if (durationMs <= 0L) return "0m"
        val totalSecs = durationMs / 1000L
        val hours = totalSecs / 3600L
        val mins = (totalSecs % 3600L) / 60L
        val secs = totalSecs % 60L
        return when {
            hours > 0 -> "${hours}h ${mins}m"
            mins > 0 -> "${mins}m ${secs}s"
            else -> "${secs}s"
        }
    }

    fun formatBytesGb(bytes: Long): String {
        if (bytes <= 0L) return "--"
        val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
        return String.format(Locale.US, "%.1f GB", gb)
    }
}
