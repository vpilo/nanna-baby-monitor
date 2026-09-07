package org.vpilo.babymonitor.data.device

import kotlinx.coroutines.flow.Flow

/**
 * Data source for raw device state readings.
 * Implementations only read the raw information.
 */
internal expect class DeviceStateDataSource() {
    val batteryLevel: Flow<Int>

    val signalLevel: Flow<Int>

    val isInternetAvailable: Flow<Boolean>
}
