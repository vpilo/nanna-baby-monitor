package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface DeviceStateRepository {
    /**
     * Battery level in percentage (0-100).
     */
    val batteryLevel: Flow<Int>

    /**
     * Signal quality in percentage (0-100).
     */
    val signalQuality: Flow<Int>

    /**
     * Network connection state.
     *
     * Note that this can update with the same value if the connection type changes, e.g. switching from Wi-Fi to mobile data.
     */
    val isInternetAvailable: Flow<Boolean>
}
