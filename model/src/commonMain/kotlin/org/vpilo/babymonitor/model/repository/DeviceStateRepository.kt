package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface DeviceStateRepository {
    val batteryLevel: Flow<Int>

    val signalQuality: Flow<Int>
}
