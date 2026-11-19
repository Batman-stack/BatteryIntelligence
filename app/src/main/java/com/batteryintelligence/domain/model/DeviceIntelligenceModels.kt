package com.batteryintelligence.domain.model

enum class ThermalSeverityLevel(val displayLabel: String) {
    NONE("Nominal"),
    LIGHT("Light Warmth"),
    MODERATE("Moderate"),
    SEVERE("Severe"),
    CRITICAL("Critical"),
    EMERGENCY("Emergency"),
    SHUTDOWN("Shutdown Imminent"),
    UNAVAILABLE("Not Available")
}

enum class DiagnosticSeverity(val displayLabel: String) {
    GOOD("Healthy"),
    INFO("Advisory"),
    WARNING("Attention")
}

data class CpuCoreFrequencyInfo(
    val coreIndex: Int,
    val isOnline: Boolean?,
    val currentFreqMhz: Int?,
    val maxFreqMhz: Int?,
    val minFreqMhz: Int?,
    val governor: String?
)

data class CpuDiagnostics(
    val socManufacturer: String?,
    val socModel: String?,
    val hardwareBoard: String,
    val supportedAbis: List<String>,
    val coreCount: Int,
    val cores: List<CpuCoreFrequencyInfo>,
    val freqReadableByKernel: Boolean
)

data class MemoryStorageDiagnostics(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val usedRamBytes: Long,
    val lowMemoryThresholdBytes: Long,
    val isLowMemory: Boolean,
    val internalTotalBytes: Long,
    val internalAvailableBytes: Long,
    val internalUsedBytes: Long
) {
    val ramUsagePercent: Float
        get() = if (totalRamBytes > 0L) ((usedRamBytes.toDouble() / totalRamBytes) * 100.0).toFloat() else 0f

    val storageUsagePercent: Float
        get() = if (internalTotalBytes > 0L) ((internalUsedBytes.toDouble() / internalTotalBytes) * 100.0).toFloat() else 0f
}

data class SystemProfileInfo(
    val manufacturer: String,
    val model: String,
    val deviceName: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String?,
    val elapsedRealtimeMs: Long,
    val awakeUptimeMs: Long,
    val deepSleepMs: Long,
    val deepSleepPercent: Float
)

data class DisplayAndSotDiagnostics(
    val refreshRateHz: Float?,
    val isAdaptiveBrightness: Boolean?,
    val rolling24hScreenOnMs: Long?,
    val rolling24hScreenOffMs: Long?,
    val sinceUnplugScreenOnMs: Long?
)

data class NetworkDiagnostics(
    val transportLabel: String,
    val isMetered: Boolean?,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
    val isConnected: Boolean
)

data class ThermalAndSensorDiagnostics(
    val thermalSeverity: ThermalSeverityLevel,
    val thermalHeadroomForecast: Float?,
    val totalHardwareSensors: Int,
    val sensorCategoryCounts: Map<String, Int>,
    val topSensorNames: List<String>
)

data class DrainDiagnosticInsight(
    val id: String,
    val title: String,
    val detail: String,
    val severity: DiagnosticSeverity
)

data class DeviceIntelligenceSnapshot(
    val timestampMs: Long,
    val cpu: CpuDiagnostics,
    val memoryStorage: MemoryStorageDiagnostics,
    val systemProfile: SystemProfileInfo,
    val displaySot: DisplayAndSotDiagnostics,
    val network: NetworkDiagnostics,
    val thermalSensors: ThermalAndSensorDiagnostics,
    val insights: List<DrainDiagnosticInsight>
)
