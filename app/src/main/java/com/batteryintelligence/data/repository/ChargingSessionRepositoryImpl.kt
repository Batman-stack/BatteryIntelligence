package com.batteryintelligence.data.repository

import com.batteryintelligence.data.datasource.BatteryHardwareDataSource
import com.batteryintelligence.data.local.dao.AppUsageSessionDao
import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.dao.ChargingSessionSampleDao
import com.batteryintelligence.data.local.dao.DailyBatteryRollupDao
import com.batteryintelligence.data.local.mapper.toDomain
import com.batteryintelligence.data.session.ChargingSessionManager
import com.batteryintelligence.domain.model.BatteryHealthReport
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargerSourceBenchmark
import com.batteryintelligence.domain.model.ChargingConfidence
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.ChargingSessionSample
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.DailyBatteryRollup
import com.batteryintelligence.domain.model.MetricSource
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ChargingSessionRepositoryImpl @Inject constructor(
    private val chargingSessionDao: ChargingSessionDao,
    private val chargingSessionSampleDao: ChargingSessionSampleDao,
    private val dailyBatteryRollupDao: DailyBatteryRollupDao,
    private val batterySampleDao: BatterySampleDao,
    private val appUsageSessionDao: AppUsageSessionDao,
    private val chargingSessionManager: ChargingSessionManager,
    private val batteryHardwareDataSource: BatteryHardwareDataSource,
    private val preferencesRepository: PreferencesRepository
) : ChargingSessionRepository {

    override fun observeChargingSessions(limit: Int): Flow<List<ChargingSession>> {
        return chargingSessionDao.observeRecentSessions(limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeActiveSession(): Flow<ChargingSession?> {
        return chargingSessionDao.observeActiveSession().map { it?.toDomain() }
    }

    override fun observeSessionById(sessionId: Long): Flow<ChargingSession?> {
        return chargingSessionDao.observeSessionById(sessionId).map { it?.toDomain() }
    }

    override fun observeSessionSamples(sessionId: Long): Flow<List<ChargingSessionSample>> {
        return chargingSessionSampleDao.observeSamplesForSession(sessionId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeChargerBenchmarks(limit: Int): Flow<List<ChargerSourceBenchmark>> {
        return chargingSessionDao.observeCompletedSessions(limit).map { entities ->
            val domainSessions = entities.map { it.toDomain() }
            domainSessions
                .groupBy { session ->
                    val tier = session.dominantSpeedTier ?: ChargingSpeedTier.UNKNOWN
                    session.chargingSource to tier
                }
                .map { (key, group) ->
                    val (source, tier) = key
                    val avgPower = group.mapNotNull { it.avgPowerWatts ?: it.peakPowerWatts }
                        .takeIf { it.isNotEmpty() }
                        ?.average()
                    val maxPower = group.mapNotNull { it.peakPowerWatts }.maxOrNull()
                    val avgEff = group.mapNotNull { it.chargingEfficiencyPercent }
                        .takeIf { it.isNotEmpty() }
                        ?.average()
                    val avgTempRise = group.mapNotNull { session ->
                        val maxT = session.peakTemperatureCelsius
                        val minT = session.minTemperatureCelsius
                        if (maxT != null && minT != null) (maxT - minT).coerceAtLeast(0f) else null
                    }.takeIf { it.isNotEmpty() }?.average()?.toFloat()

                    ChargerSourceBenchmark(
                        chargingSource = source,
                        speedTier = tier,
                        sessionCount = group.size,
                        avgPowerWatts = avgPower,
                        maxPowerWatts = maxPower,
                        avgEfficiencyPercent = avgEff,
                        avgTempRiseCelsius = avgTempRise
                    )
                }
                .sortedByDescending { it.avgPowerWatts ?: 0.0 }
        }
    }

    override fun observeCapacityTrendSessions(limit: Int): Flow<List<ChargingSession>> {
        return chargingSessionDao.observeSessionsWithCapacityEstimate(limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeDailyRollups(limit: Int): Flow<List<DailyBatteryRollup>> {
        return dailyBatteryRollupDao.observeRecentRollups(limit).map { list ->
            list.map { it.toDomain() }.sortedBy { it.dateIso }
        }
    }

    override suspend fun processSnapshotForSession(
        snapshot: BatterySnapshot,
        intelligenceState: ChargingIntelligenceState?
    ) {
        chargingSessionManager.processSnapshot(snapshot, intelligenceState)
    }

    override suspend fun getHealthReport(): BatteryHealthReport {
        val snapshot = batteryHardwareDataSource.readSnapshot()
        val prefs = preferencesRepository.preferences.first()
        val capacitySessions = chargingSessionDao.getCompletedSessionsWithCapacityEstimate(20)
        val allCompleted = chargingSessionDao.getCompletedSessions(200)

        val designCapacityMah: Double?
        val designCapacitySource: MetricSource
        if (prefs.userDesignCapacityMahOverride != null && prefs.userDesignCapacityMahOverride > 0.0) {
            designCapacityMah = prefs.userDesignCapacityMahOverride
            designCapacitySource = MetricSource.USER_CONFIGURED
        } else {
            designCapacityMah = snapshot.designCapacityMah
            designCapacitySource = snapshot.telemetrySources.designCapacitySource
        }

        val estimatedFullCapacityMah = capacitySessions
            .mapNotNull { it.estimatedCapacityMah }
            .takeIf { it.isNotEmpty() }
            ?.average()

        val stateOfHealthPercent: Int?
        val stateOfHealthSource: MetricSource
        if (snapshot.hardwareStateOfHealthPercent != null && snapshot.hardwareStateOfHealthPercent > 0) {
            stateOfHealthPercent = snapshot.hardwareStateOfHealthPercent
            stateOfHealthSource = snapshot.telemetrySources.stateOfHealthSource
        } else if (estimatedFullCapacityMah != null && designCapacityMah != null && designCapacityMah > 0.0) {
            stateOfHealthPercent = ((estimatedFullCapacityMah / designCapacityMah) * 100)
                .roundToInt()
                .coerceIn(1, 150)
            stateOfHealthSource = MetricSource.SESSION_DERIVED
        } else {
            stateOfHealthPercent = null
            stateOfHealthSource = MetricSource.UNAVAILABLE
        }

        val totalPercentCharged = allCompleted.sumOf { s ->
            val end = s.endLevelPercent ?: s.startLevelPercent
            (end - s.startLevelPercent).coerceAtLeast(0)
        }
        val estimatedCyclesFromHistory = if (totalPercentCharged > 0) {
            totalPercentCharged / 100.0
        } else {
            null
        }
        val cycleConfidence = when {
            snapshot.cycleCount != null -> ChargingConfidence.HIGH
            allCompleted.size >= 15 -> ChargingConfidence.MEDIUM
            else -> ChargingConfidence.LOW
        }

        val sessionAvgTemps = allCompleted.mapNotNull { it.avgTemperatureCelsius }
        val avgChargingTemp = sessionAvgTemps.takeIf { it.isNotEmpty() }?.map { it.toDouble() }?.average()?.toFloat()
        val maxObservedTemp = allCompleted.mapNotNull { it.maxTemperatureCelsius }.maxOrNull()
        val highTempSessions = allCompleted.count { (it.maxTemperatureCelsius ?: 0f) >= 40.0f }

        return BatteryHealthReport(
            designCapacityMah = designCapacityMah,
            designCapacitySource = designCapacitySource,
            estimatedFullCapacityMah = estimatedFullCapacityMah,
            stateOfHealthPercent = stateOfHealthPercent,
            stateOfHealthSource = stateOfHealthSource,
            cycleCount = snapshot.cycleCount,
            cycleCountSource = snapshot.telemetrySources.cycleCountSource,
            estimatedCycleCount = estimatedCyclesFromHistory,
            cycleCountConfidence = cycleConfidence,
            averageChargingTempCelsius = avgChargingTemp,
            maxObservedTempCelsius = maxObservedTemp,
            highTempSessionCount = highTempSessions,
            evaluatedSessionsCount = capacitySessions.size
        )
    }

    override fun observeHealthReport(): Flow<BatteryHealthReport> {
        return combine(
            chargingSessionDao.observeRecentSessions(20),
            preferencesRepository.preferences
        ) { _, _ ->
            getHealthReport()
        }
    }

    override suspend fun buildCsvExportReport(): String = withContext(Dispatchers.IO) {
        val sessions = chargingSessionDao.getCompletedSessions(200)
        val health = getHealthReport()
        buildString {
            appendLine("# Battery Intelligence Local Telemetry Export")
            appendLine("# ExportedAt,${Instant.now()}")
            appendLine("# StateOfHealthPercent,${health.stateOfHealthPercent ?: "N/A"},Source,${health.stateOfHealthSource.displayLabel}")
            appendLine("# DesignCapacityMah,${health.designCapacityMah?.let { String.format(Locale.US, "%.0f", it) } ?: "N/A"}")
            appendLine("# EstimatedFullCapacityMah,${health.estimatedFullCapacityMah?.let { String.format(Locale.US, "%.0f", it) } ?: "N/A"}")
            appendLine("# HardwareCycleCount,${health.cycleCount ?: "N/A"},ObservedSessionCycles,${health.estimatedCycleCount?.let { String.format(Locale.US, "%.2f", it) } ?: "0.00"}")
            appendLine()
            appendLine("session_id,start_iso,end_iso,duration_min,source,start_pct,end_pct,avg_power_w,peak_power_w,avg_current_ma,min_temp_c,max_temp_c,avg_temp_c,energy_wh,charge_mah,speed_tier,termination_reason")
            for (s in sessions) {
                val startIso = Instant.ofEpochMilli(s.startTimeMs).toString()
                val endIso = s.endTimeMs?.let { Instant.ofEpochMilli(it).toString() } ?: ""
                val durMin = String.format(Locale.US, "%.1f", s.durationMs / 60_000.0)
                val avgW = s.avgPowerWatts?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                val peakW = s.peakPowerWatts?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                val avgMa = s.avgCurrentMa?.let { String.format(Locale.US, "%.0f", it) } ?: ""
                val minT = s.minTemperatureCelsius?.let { String.format(Locale.US, "%.1f", it) } ?: ""
                val maxT = s.maxTemperatureCelsius?.let { String.format(Locale.US, "%.1f", it) } ?: ""
                val avgT = s.avgTemperatureCelsius?.let { String.format(Locale.US, "%.1f", it) } ?: ""
                val wh = s.energyAddedWh?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                val mah = s.chargeAddedUah?.let { String.format(Locale.US, "%.0f", it / 1000.0) } ?: ""
                appendLine(
                    "${s.id},$startIso,$endIso,$durMin,${s.chargingSource.name},${s.startLevelPercent},${s.endLevelPercent ?: ""},$avgW,$peakW,$avgMa,$minT,$maxT,$avgT,$wh,$mah,${s.dominantSpeedTier?.name ?: ""},${s.terminationReason.name}"
                )
            }
        }
    }

    override suspend fun pruneDataOlderThanDays(days: Int): Int = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - (days.coerceAtLeast(1) * 24L * 3_600_000L)
        val deletedSamples = batterySampleDao.deleteOlderThan(cutoff)
        val deletedCurvePoints = chargingSessionSampleDao.deleteOlderThan(cutoff)
        val deletedSessions = chargingSessionDao.deleteOlderThan(cutoff)
        val deletedUsage = appUsageSessionDao.deleteOlderThan(cutoff)
        deletedSamples + deletedCurvePoints + deletedSessions + deletedUsage
    }
}
