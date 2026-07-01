package org.vpilo.babymonitor.network.common.discovery

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.discovery.ktx.toAttributes
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.time.Duration.Companion.milliseconds

internal actual class DefaultLocalDiscoveryRepository : LocalDiscoveryRepository {
    private val discoveryService = JmDNS.create()

    private val listener = DesktopDiscoveryListener()

    private var device: Device? = null

    actual override val discoveredDevicesFlow: Flow<Set<Device>> =
        @OptIn(FlowPreview::class)
        listener.discoveredDevices
            .debounce(DISCOVERY_DEBOUNCE_TIME)
            .map { it.toSortedSet() }
            .distinctUntilChanged()

    actual override fun register(device: Device) {
        if (this.device == device) {
            return
        }
        if (this.device != null) {
            Logger.i(TAG) { "Service was already registered for ${this.device}! Changing to $device" }
            unregister()
        }
        this.device = device

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

            is Device.Client -> {
                Logger.d(TAG) { "Discovery stopped" }
                discoveryService.removeServiceListener(DISCOVERY_DESKTOP_SERVICE_TYPE, listener)
            }

            else -> {
                error("Invalid device type: $device")
            }
        }
        this.device = null
        listener.reset()
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

        private val DISCOVERY_DEBOUNCE_TIME = 500.milliseconds

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
