package com.batteryintelligence.data.repository

import com.batteryintelligence.data.local.dao.OvernightLogDao
import com.batteryintelligence.data.local.mapper.toDomain
import com.batteryintelligence.data.session.OvernightLogManager
import com.batteryintelligence.domain.model.OvernightLog
import com.batteryintelligence.domain.repository.OvernightLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OvernightLogRepositoryImpl @Inject constructor(
    private val overnightLogDao: OvernightLogDao,
    private val overnightLogManager: OvernightLogManager
) : OvernightLogRepository {

    override fun observeRecentLogs(limit: Int): Flow<List<OvernightLog>> {
        return overnightLogDao.observeRecentLogs(limit).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeLatestLog(): Flow<OvernightLog?> {
        return overnightLogDao.observeLatestLog().map { it?.toDomain() }
    }

    override suspend fun getLogForNight(nightDateIso: String): OvernightLog? {
        return overnightLogDao.getByNightDate(nightDateIso)?.toDomain()
    }

    override suspend fun evaluateOvernightWindow(referenceTimestampMs: Long) {
        overnightLogManager.evaluateOvernightWindow(referenceTimestampMs)
    }

    override suspend fun pruneLogsOlderThan(cutoffTimestampMs: Long): Int {
        return overnightLogDao.deleteOlderThan(cutoffTimestampMs)
    }
}
