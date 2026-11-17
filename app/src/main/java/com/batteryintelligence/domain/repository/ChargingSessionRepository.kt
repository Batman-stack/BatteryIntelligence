package com.batteryintelligence.domain.repository

import com.batteryintelligence.domain.model.BatteryHealthReport
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingSession
import kotlinx.coroutines.flow.Flow

interface ChargingSessionRepository {
    fun observeChargingSessions(limit: Int = 50): Flow<List<ChargingSession>>

    fun observeActiveSession(): Flow<ChargingSession?>

    suspend fun processSnapshotForSession(snapshot: BatterySnapshot)

    suspend fun getHealthReport(): BatteryHealthReport

    fun observeHealthReport(): Flow<BatteryHealthReport>
}