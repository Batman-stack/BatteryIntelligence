package com.batteryintelligence.domain.repository

import com.batteryintelligence.domain.model.BatteryHealthReport
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargerSourceBenchmark
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.ChargingSession
import com.batteryintelligence.domain.model.ChargingSessionSample
import com.batteryintelligence.domain.model.DailyBatteryRollup
import kotlinx.coroutines.flow.Flow

interface ChargingSessionRepository {
    fun observeChargingSessions(limit: Int = 50): Flow<List<ChargingSession>>

    fun observeActiveSession(): Flow<ChargingSession?>

    fun observeSessionById(sessionId: Long): Flow<ChargingSession?>

    fun observeSessionSamples(sessionId: Long): Flow<List<ChargingSessionSample>>

    fun observeChargerBenchmarks(limit: Int = 50): Flow<List<ChargerSourceBenchmark>>

    fun observeCapacityTrendSessions(limit: Int = 20): Flow<List<ChargingSession>>

    fun observeDailyRollups(limit: Int = 30): Flow<List<DailyBatteryRollup>>

    suspend fun processSnapshotForSession(
        snapshot: BatterySnapshot,
        intelligenceState: ChargingIntelligenceState? = null
    )

    suspend fun getHealthReport(): BatteryHealthReport

    fun observeHealthReport(): Flow<BatteryHealthReport>
}