package com.batteryintelligence.data.session

import com.batteryintelligence.data.local.dao.ChargingSessionDao
import com.batteryintelligence.data.local.dao.ChargingSessionSampleDao
import com.batteryintelligence.data.local.dao.DailyBatteryRollupDao
import com.batteryintelligence.data.local.entity.ChargingSessionEntity
import com.batteryintelligence.data.local.entity.ChargingSessionSampleEntity
import com.batteryintelligence.data.local.entity.DailyBatteryRollupEntity
import com.batteryintelligence.domain.model.BatteryHealthCondition
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.model.ChargingConfidence
import com.batteryintelligence.domain.model.ChargingEtaEstimate
import com.batteryintelligence.domain.model.ChargingIntelligenceState
import com.batteryintelligence.domain.model.ChargingPhase
import com.batteryintelligence.domain.model.ChargingSource
import com.batteryintelligence.domain.model.ChargingSpeedTier
import com.batteryintelligence.domain.model.ChargingStatus
import com.batteryintelligence.domain.model.ChargingTerminationReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChargingSessionManagerTest {

    private lateinit var sessionDao: FakeChargingSessionDao
    private lateinit var sampleDao: FakeChargingSessionSampleDao
    private lateinit var rollupDao: FakeDailyBatteryRollupDao
    private lateinit var manager: ChargingSessionManager

    @Before
    fun setUp() {
        sessionDao = FakeChargingSessionDao()
        sampleDao = FakeChargingSessionSampleDao()
        rollupDao = FakeDailyBatteryRollupDao()
        manager = ChargingSessionManager(sessionDao, sampleDao, rollupDao)
    }

    @Test
    fun `full charging lifecycle records session averages, curve samples, termination reason, and daily rollup`() = runTest {
        val baseTime = 1_700_000_000_000L

        val plugIn = manager.processSnapshot(
            snapshot = snapshot(baseTime, level = 50, voltageMv = 4200, currentMa = 4000.0, tempC = 30.0f, chargeUah = 2_500_000L),
            intelligenceState = intel(powerW = 16.8, tier = ChargingSpeedTier.NORMAL, phase = ChargingPhase.CC, eta100Ms = 1_800_000L)
        )
        assertTrue(plugIn is SessionTransition.PluggedIn)

        val midUpdate = manager.processSnapshot(
            snapshot = snapshot(baseTime + 900_000L, level = 82, voltageMv = 4400, currentMa = 2000.0, tempC = 34.0f, chargeUah = 4_100_000L),
            intelligenceState = intel(powerW = 8.8, tier = ChargingSpeedTier.NORMAL, phase = ChargingPhase.CV, slope = 0.4f)
        )
        assertTrue(midUpdate is SessionTransition.ActiveUpdated)

        val unplug = manager.processSnapshot(
            snapshot = snapshot(
                timestampMs = baseTime + 1_800_000L,
                level = 100,
                voltageMv = 4400,
                currentMa = 0.0,
                tempC = 31.0f,
                chargeUah = 5_000_000L,
                status = ChargingStatus.DISCHARGING,
                source = ChargingSource.NONE
            ),
            intelligenceState = null
        )
        assertTrue(unplug is SessionTransition.Unplugged)
        val completed = (unplug as SessionTransition.Unplugged).completedSession
        assertFalse(completed.isActive)
        assertEquals(ChargingTerminationReason.REACHED_FULL, completed.terminationReason)
        assertEquals(1_800_000L, completed.durationMs)
        assertEquals(2_500_000L, completed.chargeAddedUah)
        assertEquals(82, completed.ccCvTransitionPercent)
        assertNotNull(completed.estimatedCapacityMah)
        assertEquals(5000.0, completed.estimatedCapacityMah!!, 1.0)
        assertEquals(3, sampleDao.allSamples().size)
        assertEquals(1, rollupDao.allRollups().size)
        assertEquals(1, rollupDao.allRollups().first().sessionCount)
    }

    @Test
    fun `stale active session gap closes prior session cleanly with STALE_GAP_CLOSED`() = runTest {
        val baseTime = 1_700_000_000_000L
        manager.processSnapshot(
            snapshot(baseTime, level = 30, voltageMv = 4100, currentMa = 3000.0, tempC = 29.0f, chargeUah = 1_500_000L),
            intel(powerW = 12.3, tier = ChargingSpeedTier.NORMAL, phase = ChargingPhase.CC)
        )

        // 45 minutes later (> 30m stale threshold)
        val transition = manager.processSnapshot(
            snapshot(baseTime + 45L * 60_000L, level = 60, voltageMv = 4200, currentMa = 3000.0, tempC = 31.0f, chargeUah = 3_000_000L),
            intel(powerW = 12.6, tier = ChargingSpeedTier.NORMAL, phase = ChargingPhase.CC)
        )
        assertTrue(transition is SessionTransition.PluggedIn)
        val sessions = sessionDao.allSessions()
        assertEquals(2, sessions.size)
        assertEquals(ChargingTerminationReason.STALE_GAP_CLOSED, sessions.first().terminationReason)
        assertTrue(sessions.last().isActive)
    }

    private fun snapshot(
        timestampMs: Long,
        level: Int,
        voltageMv: Int,
        currentMa: Double,
        tempC: Float,
        chargeUah: Long,
        status: ChargingStatus = ChargingStatus.CHARGING,
        source: ChargingSource = ChargingSource.AC
    ): BatterySnapshot {
        return BatterySnapshot(
            timestampMs = timestampMs,
            levelPercent = level,
            levelPrecisePercent = level.toFloat(),
            chargingStatus = status,
            chargingSource = source,
            healthCondition = BatteryHealthCondition.GOOD,
            isBatteryPresent = true,
            isBatteryLow = false,
            technology = "Li-ion",
            temperatureCelsius = tempC,
            voltageMv = voltageMv,
            currentNowMa = currentMa,
            currentAverageMa = currentMa,
            signedCurrentMa = currentMa,
            isNetBatteryDischargingWhilePlugged = false,
            chargeCounterUah = chargeUah,
            energyCounterNwh = null,
            cycleCount = null,
            hardwareStateOfHealthPercent = null,
            designCapacityMah = 5000.0,
            chargeTimeRemainingMs = null
        )
    }

    private fun intel(
        powerW: Double,
        tier: ChargingSpeedTier,
        phase: ChargingPhase,
        slope: Float? = null,
        eta100Ms: Long? = null
    ): ChargingIntelligenceState {
        return ChargingIntelligenceState(
            smoothedPowerWatts = powerW,
            speedTier = tier,
            chargingPhase = phase,
            thermalSlopeCPerMin = slope,
            etaEstimate = eta100Ms?.let {
                ChargingEtaEstimate(
                    timeToTargetMs = it,
                    targetPercent = 80,
                    timeTo80Ms = it,
                    timeTo100Ms = it,
                    clockTimeAtTargetMs = null,
                    clockTimeAt100Ms = null,
                    confidence = ChargingConfidence.MEDIUM,
                    chargeRatePercentPerMin = 1.0
                )
            }
        )
    }

    private class FakeChargingSessionDao : ChargingSessionDao {
        private val items = MutableStateFlow<List<ChargingSessionEntity>>(emptyList())
        private var nextId = 1L

        fun allSessions(): List<ChargingSessionEntity> = items.value

        override suspend fun insertSession(session: ChargingSessionEntity): Long {
            val id = if (session.id == 0L) nextId++ else session.id
            val stored = session.copy(id = id)
            items.value = items.value.filterNot { it.id == id } + stored
            return id
        }

        override suspend fun updateSession(session: ChargingSessionEntity) {
            items.value = items.value.map { if (it.id == session.id) session else it }
        }

        override suspend fun getSessionById(sessionId: Long) = items.value.find { it.id == sessionId }

        override fun observeSessionById(sessionId: Long): Flow<ChargingSessionEntity?> =
            items.map { list -> list.find { it.id == sessionId } }

        override suspend fun getActiveSession(): ChargingSessionEntity? =
            items.value.filter { it.isActive }.maxByOrNull { it.startTimeMs }

        override fun observeActiveSession(): Flow<ChargingSessionEntity?> =
            items.map { list -> list.filter { it.isActive }.maxByOrNull { it.startTimeMs } }

        override fun observeRecentSessions(limit: Int): Flow<List<ChargingSessionEntity>> =
            items.map { list -> list.sortedByDescending { it.startTimeMs }.take(limit) }

        override fun observeCompletedSessions(limit: Int): Flow<List<ChargingSessionEntity>> =
            items.map { list -> list.filter { !it.isActive }.sortedByDescending { it.startTimeMs }.take(limit) }

        override suspend fun getSessionsInWindow(startMs: Long, endMs: Long): List<ChargingSessionEntity> =
            items.value.filter { it.startTimeMs in startMs..endMs }.sortedBy { it.startTimeMs }

        override suspend fun getCompletedSessionsWithCapacityEstimate(limit: Int): List<ChargingSessionEntity> =
            items.value.filter { !it.isActive && it.estimatedCapacityMah != null }.sortedByDescending { it.startTimeMs }.take(limit)

        override fun observeSessionsWithCapacityEstimate(limit: Int): Flow<List<ChargingSessionEntity>> =
            items.map { list -> list.filter { !it.isActive && it.estimatedCapacityMah != null }.sortedBy { it.startTimeMs }.take(limit) }

        override suspend fun closeOtherActiveSessions(excludeId: Long) {
            items.value = items.value.map {
                if (it.isActive && it.id != excludeId) {
                    it.copy(isActive = false, terminationReason = ChargingTerminationReason.STALE_GAP_CLOSED)
                } else {
                    it
                }
            }
        }

        override suspend fun deleteOlderThan(cutoffTimestampMs: Long): Int {
            val before = items.value.size
            items.value = items.value.filterNot { !it.isActive && it.startTimeMs < cutoffTimestampMs }
            return before - items.value.size
        }
    }

    private class FakeChargingSessionSampleDao : ChargingSessionSampleDao {
        private val items = MutableStateFlow<List<ChargingSessionSampleEntity>>(emptyList())
        private var nextId = 1L

        fun allSamples(): List<ChargingSessionSampleEntity> = items.value

        override suspend fun insertSample(sample: ChargingSessionSampleEntity): Long {
            val id = if (sample.id == 0L) nextId++ else sample.id
            items.value = items.value + sample.copy(id = id)
            return id
        }

        override fun observeSamplesForSession(sessionId: Long): Flow<List<ChargingSessionSampleEntity>> =
            items.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.timestampMs } }

        override suspend fun getSamplesForSession(sessionId: Long): List<ChargingSessionSampleEntity> =
            items.value.filter { it.sessionId == sessionId }.sortedBy { it.timestampMs }

        override suspend fun getLatestSampleForSession(sessionId: Long): ChargingSessionSampleEntity? =
            items.value.filter { it.sessionId == sessionId }.maxByOrNull { it.timestampMs }

        override suspend fun deleteOlderThan(cutoffTimestampMs: Long): Int {
            val before = items.value.size
            items.value = items.value.filterNot { it.timestampMs < cutoffTimestampMs }
            return before - items.value.size
        }

        override suspend fun pruneBeyondRecentSessions(keepSessionCount: Int): Int = 0
    }

    private class FakeDailyBatteryRollupDao : DailyBatteryRollupDao {
        private val items = MutableStateFlow<List<DailyBatteryRollupEntity>>(emptyList())

        fun allRollups(): List<DailyBatteryRollupEntity> = items.value

        override suspend fun upsertRollup(rollup: DailyBatteryRollupEntity) {
            items.value = items.value.filterNot { it.dateIso == rollup.dateIso } + rollup
        }

        override suspend fun getByDate(dateIso: String): DailyBatteryRollupEntity? =
            items.value.find { it.dateIso == dateIso }

        override fun observeRollupsSince(sinceIso: String): Flow<List<DailyBatteryRollupEntity>> =
            items.map { list -> list.filter { it.dateIso >= sinceIso }.sortedBy { it.dateIso } }

        override fun observeRecentRollups(limit: Int): Flow<List<DailyBatteryRollupEntity>> =
            items.map { list -> list.sortedByDescending { it.dateIso }.take(limit) }
    }
}
