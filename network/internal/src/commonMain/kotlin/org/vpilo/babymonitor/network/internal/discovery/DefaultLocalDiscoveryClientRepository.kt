package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.repository.DiscoveredDeviceCacheRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryClientRepository
import kotlin.coroutines.CoroutineContext

internal class DefaultLocalDiscoveryClientRepository(
    private val coroutineContext: CoroutineContext,
    private val localDiscoveryDataSource: LocalDiscoveryDataSource,
    private val discoveredDeviceCacheRepository: DiscoveredDeviceCacheRepository,
) : LocalDiscoveryClientRepository {
    private var device: Device? = null

    private val discoveredDevices: MutableStateFlow<Map<DeviceId, Device>> = MutableStateFlow<Map<DeviceId, Device>>(emptyMap())

    override val discoveredDevicesFlow: Flow<Set<Device>> =
        discoveredDevices.map { it.values.toSet() }

    private val mutableIsRegisteredFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val isRegisteredFlow: Flow<Boolean> = mutableIsRegisteredFlow.asStateFlow()

    private var cacheUpdateJob: Job? = null

    private val watchdog =
        DeviceWatchdog(
            devicesFlow = discoveredDevices,
            onDeviceUnreachable = { removed ->
                Logger.i(TAG) { "Device unreachable: $removed" }
                discoveredDevices.update { it - removed.id }
            },
            onDeviceReturned = { returned ->
                Logger.i(TAG) { "Device returned: $returned" }
                discoveredDevices.update { it + (returned.id to returned) }
            },
            coroutineScope = CoroutineScope(coroutineContext),
        )

    override fun startDiscovery(device: Device) {
        if (this.device == device) {
            return
        }
        if (this.device != null) {
            Logger.i(TAG) { "Discovery was already active for ${this.device}! Changing to $device" }
            stopDiscovery()
        }
        check(device is Device.Client || device is Device.Relay) { "$device was not a client device type" }

        this.device = device
        mutableIsRegisteredFlow.value = true

        try {
            Logger.d(TAG) { "Discovering" }
            localDiscoveryDataSource.startDiscovery(device, discoveredDevices)
        } catch (ex: IOException) {
            Logger.e(TAG, ex) { "Failed to start discovery" }
        }
        watchdog.startWatching()

        cacheUpdateJob?.cancel()
        cacheUpdateJob =
            CoroutineScope(coroutineContext).launch {
                discoveredDevices.collect {
                    val devices = it.values.toSet()
                    Logger.d(TAG) { "Discovered devices: $devices" }
                    discoveredDeviceCacheRepository.set(devices)
                }
            }
    }

    override fun stopDiscovery() {
        if (this.device == null) {
            Logger.d(TAG) { "Discovery was not active" }
            return
        }

        Logger.d(TAG) { "Stopped" }
        localDiscoveryDataSource.stopDiscovery()

        this.device = null
        cacheUpdateJob?.cancel()
        cacheUpdateJob = null
        mutableIsRegisteredFlow.value = false
        watchdog.stopWatching()
    }

    override fun refresh() {
        val currentDevice = device ?: return
        stopDiscovery()
        startDiscovery(currentDevice)
    }

    private companion object {
        const val TAG = "DefaultLocalDiscoveryClientRepository"
    }
}
