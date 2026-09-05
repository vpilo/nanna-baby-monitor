package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.io.IOException
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.toAttributes
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.coroutines.CoroutineContext

internal actual class DefaultLocalDiscoveryRepository actual constructor(
    coroutineContext: CoroutineContext,
) : LocalDiscoveryRepository {
    private val discoveredDevices: MutableStateFlow<Map<DeviceId, Device>> = MutableStateFlow(emptyMap())

    private val discoveryService = JmDNS.create()

    private val listener = DesktopDiscoveryListener(discoveredDevices)

    private var device: Device? = null

    actual override val discoveredDevicesFlow: Flow<Set<Device>> =
        @OptIn(FlowPreview::class)
        discoveredDevices
            .debounce(LocalDiscoveryRepository.DISCOVERY_DEBOUNCE_TIME)
            .map { it.values.toSortedSet() }
            .distinctUntilChanged()

    private val mutableIsRegisteredFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    actual override val isRegisteredFlow: Flow<Boolean> = mutableIsRegisteredFlow.asStateFlow()

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

    actual override fun register(device: Device) {
        if (this.device == device) {
            return
        }
        if (this.device != null) {
            Logger.i(TAG) { "Service was already registered for ${this.device}! Changing to $device" }
            unregister()
        }
        this.device = device
        listener.reset(device)
        mutableIsRegisteredFlow.value = true

        try {
            when (device) {
                is Device.LocalServer -> {
                    Logger.d(TAG) { "Registered service: $device" }
                    discoveryService.registerService(createServiceInfo(device))
                }

                is Device.Client,
                is Device.Relay,
                    -> {
                        Logger.d(TAG) { "Discovering services" }
                        discoveryService.addServiceListener(DISCOVERY_DESKTOP_SERVICE_TYPE, listener)
                        watchdog.startWatching()
                    }

                else -> {
                    error("Invalid device type: $device")
                }
            }
        } catch (ex: IOException) {
            Logger.e(TAG, ex) { "Failed to register server service" }
        }
    }

    actual override fun unregister() {
        val device =
            if (this.device == null) {
                Logger.d(TAG) { "Service was not registered" }
                return
            } else {
                this.device
            }

        when (device) {
            is Device.LocalServer -> {
                Logger.d(TAG) { "Unregistered service" }
                discoveryService.unregisterAllServices()
            }

            is Device.Client,
            is Device.Relay,
                -> {
                    Logger.d(TAG) { "Discovery stopped" }
                    discoveryService.removeServiceListener(DISCOVERY_DESKTOP_SERVICE_TYPE, listener)
                }

            else -> {
                error("Invalid device type: $device")
            }
        }
        this.device = null
        listener.reset()
        mutableIsRegisteredFlow.value = false
        watchdog.stopWatching()
    }

    actual override fun refresh() {
        val currentDevice = device ?: return
        unregister()
        register(currentDevice)
    }

    companion object {
        val TAG = DefaultLocalDiscoveryRepository::class

        // JmDNS requires the ".local." suffix
        private const val DISCOVERY_DESKTOP_SERVICE_TYPE = "_${Constants.DISCOVERY_SERVICE_TYPE}._tcp.local."

        private fun createServiceInfo(device: Device): ServiceInfo =
            ServiceInfo.create(
                DISCOVERY_DESKTOP_SERVICE_TYPE,
                device.idString,
                Constants.SERVICE_PORT,
                0,
                0,
                device.toAttributes(),
            )
    }
}
