package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device
import kotlin.time.Duration.Companion.milliseconds

interface LocalDiscoveryRepository {
    val discoveredDevicesFlow: Flow<Set<Device>>

    val isRegisteredFlow: Flow<Boolean>

    fun register(device: Device)

    fun unregister()

    fun refresh()

    companion object {
        val DISCOVERY_DEBOUNCE_TIME = 500.milliseconds
    }
}
