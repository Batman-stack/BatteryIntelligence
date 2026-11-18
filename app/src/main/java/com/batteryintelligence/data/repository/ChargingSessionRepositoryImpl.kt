package com.batteryintelligence.data.repository

import com.batteryintelligence.data.datasource.BatteryHardwareDataSource
import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.dao.ChargingSessionSampleDao
import com.batteryintelligence.data.local.dao.DailyBatteryRollupDao
import com.batteryintelligence.data.local.mapper.toDomain
import com.batteryintelligence.data.session.ChargingSessionManager
import com.batteryintelligence.domain.model.BatteryHealthReport
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargerSourceBenchmark
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.ChargingSessionSample
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.DailyBatteryRollup
import com.batteryintelligence.domain.model.MetricSource
import com.batteryintelligence.domain.repository.ChargingSessionRepository
import com.batteryintelligence.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ChargingSessionRepositoryImpl @Inject constructor(
    private val chargingSessionDao: ChargingSessionDao,
    private val chargingSessionSampleDao: ChargingSessionSampleDao,
    private val dailyBatteryRollupDao: DailyBatteryRollupDao,
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
        val completedSessions = chargingSessionDao.getCompletedSessionsWithCapacityEstimate(20)

        val designCapacityMah: Double?
        val designCapacitySource: MetricSource
        if (prefs.userDesignCapacityMahOverride != null && prefs.userDesignCapacityMahOverride > 0.0) {
            designCapacityMah = prefs.userDesignCapacityMahOverride
            designCapacitySource = MetricSource.USER_CONFIGURED
        } else {
            designCapacityMah = snapshot.designCapacityMah
            designCapacitySource = snapshot.telemetrySources.designCapacitySource
        }

        val estimatedFullCapacityMah = if (completedSessions.isNotEmpty()) {
            val validEstimates = completedSessions.mapNotNull { it.estimatedCapacityMah }
            if (validEstimates.isNotEmpty()) {
                validEstimates.average()
            } else {
                null
            }
        } else {
            null
        }

        val stateOfHealthPercent: Int?
        val stateOfHealthSource: MetricSource
        if (snapshot.hardwareStateOfHealthPercent != null && snapshot.hardwareStateOfHealthPercent > 0) {
            stateOfHealthPercent = snapshot.hardwareStateOfHealthPercent
            stateOfHealthSource = snapshot.telemetrySources.stateOfHealthSource
        } else if (estimatedFullCapacityMah != null && designCapacityMah != null && designCapacityMah > 0.0) {
            stateOfHealthPercent = ((estimatedFullCapacityMah / designCapacityMah) * 100).roundToInt().coerceIn(1, 150)
            stateOfHealthSource = MetricSource.SESSION_DERIVED
        } else {
            stateOfHealthPercent = null
            stateOfHealthSource = MetricSource.UNAVAILABLE
        }

        return BatteryHealthReport(
            designCapacityMah = designCapacityMah,
            designCapacitySource = designCapacitySource,
            estimatedFullCapacityMah = estimatedFullCapacityMah,
            stateOfHealthPercent = stateOfHealthPercent,
            stateOfHealthSource = stateOfHealthSource,
            cycleCount = snapshot.cycleCount,
            cycleCountSource = snapshot.telemetrySources.cycleCountSource,
            evaluatedSessionsCount = completedSessions.size
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
}
