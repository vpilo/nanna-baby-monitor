package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface DeviceStateRepository {
    val batteryLevel: Flow<Int>

    val signalQuality: Flow<Int>

    /**
     * Network connection state.
     *
     * Note that this can update with the same value if the connection is changed (e.g. switch from Wi-Fi to mobile data).
     */
    val isInternetAvailable: Flow<Boolean>
}
