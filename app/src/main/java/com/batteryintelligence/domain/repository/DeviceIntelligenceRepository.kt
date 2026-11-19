package com.batteryintelligence.domain.repository

import com.batteryintelligence.domain.model.DeviceIntelligenceSnapshot

interface DeviceIntelligenceRepository {
    suspend fun readDeviceIntelligence(nowMs: Long = System.currentTimeMillis()): DeviceIntelligenceSnapshot
}
