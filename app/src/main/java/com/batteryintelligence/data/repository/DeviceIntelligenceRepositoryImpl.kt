package com.batteryintelligence.data.repository

import com.batteryintelligence.data.datasource.DeviceIntelligenceDataSource
import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.dao.OvernightLogDao
import com.batteryintelligence.data.local.mapper.toDomain
import com.batteryintelligence.domain.intelligence.DeviceDrainDiagnosticsAnalyzer
import com.batteryintelligence.domain.model.DeviceIntelligenceSnapshot
import com.batteryintelligence.domain.repository.AppUsageRepository
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.DeviceIntelligenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIntelligenceRepositoryImpl @Inject constructor(
    private val deviceDataSource: DeviceIntelligenceDataSource,
    private val appUsageRepository: AppUsageRepository,
    private val chargingSessionRepository: ChargingSessionRepository,
    private val chargingSessionDao: ChargingSessionDao,
    private val overnightLogDao: OvernightLogDao
) : DeviceIntelligenceRepository {

    override suspend fun readDeviceIntelligence(nowMs: Long): DeviceIntelligenceSnapshot =
        withContext(Dispatchers.IO) {
            val cpu = deviceDataSource.readCpuDiagnostics()
            val memoryStorage = deviceDataSource.readMemoryStorageDiagnostics()
            val systemProfile = deviceDataSource.readSystemProfile()
            val network = deviceDataSource.readNetworkDiagnostics()
            val thermalSensors = deviceDataSource.readThermalAndSensorDiagnostics()

            val usageReport = if (appUsageRepository.hasUsageStatsPermission()) {
                runCatching { appUsageRepository.getRolling24HourUsageReport(nowMs) }.getOrNull()
            } else {
                null
            }

            val lastCompletedEndMs = chargingSessionDao.getCompletedSessions(limit = 1)
                .firstOrNull()
                ?.endTimeMs
            val sinceUnplugSot = if (usageReport != null && lastCompletedEndMs != null && lastCompletedEndMs < nowMs) {
                usageReport.apps
                    .flatMap { it.sessions }
                    .filter { it.endTimeMs > lastCompletedEndMs }
                    .sumOf { s ->
                        val start = maxOf(s.startTimeMs, lastCompletedEndMs)
                        (s.endTimeMs - start).coerceAtLeast(0L)
                    }
            } else {
                null
            }

            val displaySot = deviceDataSource.readDisplayDiagnostics(
                rolling24hScreenOnMs = usageReport?.screenOnTimeMs,
                sinceUnplugScreenOnMs = sinceUnplugSot
            )

            val healthReport = runCatching { chargingSessionRepository.getHealthReport() }.getOrNull()
            val latestOvernight = runCatching { overnightLogDao.observeLatestLog().firstOrNull()?.toDomain() }.getOrNull()

            val insights = DeviceDrainDiagnosticsAnalyzer.analyze(
                systemProfile = systemProfile,
                memoryStorage = memoryStorage,
                displaySot = displaySot,
                thermalSeverity = thermalSensors.thermalSeverity,
                healthReport = healthReport,
                usageReport = usageReport,
                latestOvernightLog = latestOvernight
            )

            DeviceIntelligenceSnapshot(
                timestampMs = nowMs,
                cpu = cpu,
                memoryStorage = memoryStorage,
                systemProfile = systemProfile,
                displaySot = displaySot,
                network = network,
                thermalSensors = thermalSensors,
                insights = insights
            )
        }
}
