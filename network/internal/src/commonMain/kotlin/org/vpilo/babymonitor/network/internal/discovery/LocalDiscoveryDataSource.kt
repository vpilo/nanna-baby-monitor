package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import kotlin.coroutines.CoroutineContext

internal expect class LocalDiscoveryDataSource(
    coroutineContext: CoroutineContext,
) {
    fun startDiscovery(
        device: Device,
        discoveredDevices: MutableStateFlow<Map<DeviceId, Device>>,
    )

    fun stopDiscovery()
}
