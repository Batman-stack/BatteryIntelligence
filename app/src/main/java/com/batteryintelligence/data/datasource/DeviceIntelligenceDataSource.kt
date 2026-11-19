package com.batteryintelligence.data.datasource

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.view.Display
import com.batteryintelligence.domain.model.CpuCoreFrequencyInfo
import com.batteryintelligence.domain.model.CpuDiagnostics
import com.batteryintelligence.domain.model.DisplayAndSotDiagnostics
import com.batteryintelligence.domain.model.MemoryStorageDiagnostics
import com.batteryintelligence.domain.model.NetworkDiagnostics
import com.batteryintelligence.domain.model.SystemProfileInfo
import com.batteryintelligence.domain.model.ThermalAndSensorDiagnostics
import com.batteryintelligence.domain.model.ThermalSeverityLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIntelligenceDataSource @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun readCpuDiagnostics(): CpuDiagnostics {
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cores = mutableListOf<CpuCoreFrequencyInfo>()
        var anyFreqReadable = false

        for (i in 0 until coreCount) {
            val base = "/sys/devices/system/cpu/cpu$i"
            val online = readIntFile("$base/online")?.let { it == 1 }
            val curKhz = readLongFile("$base/cpufreq/scaling_cur_freq")
                ?: readLongFile("$base/cpufreq/cpuinfo_cur_freq")
            val maxKhz = readLongFile("$base/cpufreq/cpuinfo_max_freq")
                ?: readLongFile("$base/cpufreq/scaling_max_freq")
            val minKhz = readLongFile("$base/cpufreq/cpuinfo_min_freq")
                ?: readLongFile("$base/cpufreq/scaling_min_freq")
            val governor = readTextFile("$base/cpufreq/scaling_governor")

            if (curKhz != null || maxKhz != null) {
                anyFreqReadable = true
            }
            cores.add(
                CpuCoreFrequencyInfo(
                    coreIndex = i,
                    isOnline = online,
                    currentFreqMhz = curKhz?.let { (it / 1000L).toInt() },
                    maxFreqMhz = maxKhz?.let { (it / 1000L).toInt() },
                    minFreqMhz = minKhz?.let { (it / 1000L).toInt() },
                    governor = governor
                )
            )
        }

        val socManufacturer = Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN }
        val socModel = Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN }

        return CpuDiagnostics(
            socManufacturer = socManufacturer,
            socModel = socModel,
            hardwareBoard = listOf(Build.HARDWARE, Build.BOARD)
                .filter { it.isNotBlank() && it != Build.UNKNOWN }
                .distinct()
                .joinToString(" / ")
                .ifBlank { "Unknown" },
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            coreCount = coreCount,
            cores = cores,
            freqReadableByKernel = anyFreqReadable
        )
    }

    fun readMemoryStorageDiagnostics(): MemoryStorageDiagnostics {
        val am = context.getSystemService(ActivityManager::class.java)
        val memInfo = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem.coerceAtLeast(0L)
        val availRam = memInfo.availMem.coerceIn(0L, totalRam)
        val usedRam = (totalRam - availRam).coerceAtLeast(0L)

        val statFs = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
        val totalStorage = statFs?.totalBytes?.coerceAtLeast(0L) ?: 0L
        val availStorage = statFs?.availableBytes?.coerceIn(0L, totalStorage) ?: 0L
        val usedStorage = (totalStorage - availStorage).coerceAtLeast(0L)

        return MemoryStorageDiagnostics(
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            usedRamBytes = usedRam,
            lowMemoryThresholdBytes = memInfo.threshold.coerceAtLeast(0L),
            isLowMemory = memInfo.lowMemory,
            internalTotalBytes = totalStorage,
            internalAvailableBytes = availStorage,
            internalUsedBytes = usedStorage
        )
    }

    fun readSystemProfile(): SystemProfileInfo {
        val elapsedRealtime = SystemClock.elapsedRealtime().coerceAtLeast(1L)
        val awakeUptime = SystemClock.uptimeMillis().coerceIn(0L, elapsedRealtime)
        val deepSleep = (elapsedRealtime - awakeUptime).coerceAtLeast(0L)
        val deepSleepPct = ((deepSleep.toDouble() / elapsedRealtime.toDouble()) * 100.0)
            .toFloat()
            .coerceIn(0f, 100f)

        return SystemProfileInfo(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            deviceName = Build.DEVICE,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() },
            elapsedRealtimeMs = elapsedRealtime,
            awakeUptimeMs = awakeUptime,
            deepSleepMs = deepSleep,
            deepSleepPercent = deepSleepPct
        )
    }

    fun readDisplayDiagnostics(
        rolling24hScreenOnMs: Long?,
        sinceUnplugScreenOnMs: Long?
    ): DisplayAndSotDiagnostics {
        val displayManager = context.getSystemService(DisplayManager::class.java)
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val refreshRate = defaultDisplay?.refreshRate?.takeIf { it.isFinite() && it > 0f }

        val adaptiveBrightness = runCatching {
            val mode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            )
            mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        }.getOrNull()

        val rollingOffMs = rolling24hScreenOnMs?.let { onMs ->
            (24L * 3_600_000L - onMs).coerceAtLeast(0L)
        }

        return DisplayAndSotDiagnostics(
            refreshRateHz = refreshRate,
            isAdaptiveBrightness = adaptiveBrightness,
            rolling24hScreenOnMs = rolling24hScreenOnMs,
            rolling24hScreenOffMs = rollingOffMs,
            sinceUnplugScreenOnMs = sinceUnplugScreenOnMs
        )
    }

    fun readNetworkDiagnostics(): NetworkDiagnostics {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return NetworkDiagnostics("Unavailable", null, null, null, false)
        val activeNetwork = cm.activeNetwork
            ?: return NetworkDiagnostics("Offline", null, null, null, false)
        val caps = cm.getNetworkCapabilities(activeNetwork)
            ?: return NetworkDiagnostics("Offline", null, null, null, false)

        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }

        val isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val down = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 }
        val up = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 }

        return NetworkDiagnostics(
            transportLabel = transport,
            isMetered = isMetered,
            downstreamKbps = down,
            upstreamKbps = up,
            isConnected = true
        )
    }

    fun readThermalAndSensorDiagnostics(): ThermalAndSensorDiagnostics {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val thermalLevel = if (powerManager != null) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalSeverityLevel.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalSeverityLevel.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalSeverityLevel.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalSeverityLevel.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalSeverityLevel.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThermalSeverityLevel.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalSeverityLevel.SHUTDOWN
                else -> ThermalSeverityLevel.UNAVAILABLE
            }
        } else {
            ThermalSeverityLevel.UNAVAILABLE
        }

        val headroom = runCatching {
            powerManager?.getThermalHeadroom(30)?.takeIf { it.isFinite() && it >= 0f }
        }.getOrNull()

        val sensorManager = context.getSystemService(SensorManager::class.java)
        val allSensors = runCatching { sensorManager?.getSensorList(Sensor.TYPE_ALL).orEmpty() }
            .getOrDefault(emptyList())

        val categoryCounts = mutableMapOf<String, Int>()
        for (sensor in allSensors) {
            val cat = when (sensor.type) {
                Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_LINEAR_ACCELERATION,
                Sensor.TYPE_GRAVITY, Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_STEP_COUNTER,
                Sensor.TYPE_STEP_DETECTOR -> "Motion"
                Sensor.TYPE_LIGHT, Sensor.TYPE_PROXIMITY, Sensor.TYPE_PRESSURE,
                Sensor.TYPE_AMBIENT_TEMPERATURE, Sensor.TYPE_RELATIVE_HUMIDITY -> "Environment"
                Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_GAME_ROTATION_VECTOR,
                Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Position"
                else -> "Other"
            }
            categoryCounts[cat] = (categoryCounts[cat] ?: 0) + 1
        }

        return ThermalAndSensorDiagnostics(
            thermalSeverity = thermalLevel,
            thermalHeadroomForecast = headroom,
            totalHardwareSensors = allSensors.size,
            sensorCategoryCounts = categoryCounts,
            topSensorNames = allSensors.map { it.name }.distinct().take(12)
        )
    }

    private fun readTextFile(path: String): String? {
        return runCatching {
            val file = File(path)
            if (file.exists() && file.canRead()) {
                file.readText().trim().takeIf { it.isNotEmpty() }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun readLongFile(path: String): Long? = readTextFile(path)?.toLongOrNull()
    private fun readIntFile(path: String): Int? = readTextFile(path)?.toIntOrNull()
}
