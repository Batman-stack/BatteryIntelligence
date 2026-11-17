package com.batteryintelligence.data.repository

import com.batteryintelligence.data.datasource.BatteryHardwareDataSource
import com.batteryintelligence.data.local.dao.BatterySampleDao
import com.batteryintelligence.data.local.mapper.toDomain
import com.batteryintelligence.data.local.mapper.toSampleEntity
import com.batteryintelligence.domain.model.BatterySampleRecord
import com.batteryintelligence.domain.model.BatterySnapshot
import com.batteryintelligence.domain.repository.BatteryTelemetryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryTelemetryRepositoryImpl @Inject constructor(
    private val batteryHardwareDataSource: BatteryHardwareDataSource,
    private val batterySampleDao: BatterySampleDao
) : BatteryTelemetryRepository {

    override suspend fun getCurrentSnapshot(): BatterySnapshot {
        return batteryHardwareDataSource.getCurrentSnapshot()
    }

    override fun observeLiveTelemetry(samplingIntervalMs: Long): Flow<BatterySnapshot> {
        return batteryHardwareDataSource.observeBatteryTelemetry(samplingIntervalMs)
    }

    override suspend fun recordSample(snapshot: BatterySnapshot) {
        batterySampleDao.insertSample(snapshot.toSampleEntity())
    }

    override fun observeRecentSamples(sinceTimestampMs: Long): Flow<List<BatterySampleRecord>> {
        return batterySampleDao.observeSamplesSince(sinceTimestampMs).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun pruneSamplesOlderThan(cutoffTimestampMs: Long): Int {
        return batterySampleDao.deleteOlderThan(cutoffTimestampMs)
    }
}
