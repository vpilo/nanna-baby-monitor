package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.toDeviceOrNull
import org.vpilo.babymonitor.network.model.Constants
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

internal class DesktopDiscoveryListener(
    private val discoveredDevices: MutableStateFlow<Map<DeviceId, Device>>,
) : ServiceListener {
    private var device: Device? = null

    fun reset(device: Device? = null) {
        this.device = device
        discoveredDevices.value = emptyMap()
    }

    override fun serviceAdded(event: ServiceEvent) {
        event.dns.requestServiceInfo(event.type, event.name)
    }

    override fun serviceResolved(event: ServiceEvent) {
        val added = event.toDeviceOrNull() ?: return
        if (added.id == device?.id) return

        if (added.addresses.isEmpty()) {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) { "Device resolved with no hosts: $added" }
            return
        }

        // Log only new or renamed devices to reduce verbosity.
        val known = discoveredDevices.value[added.id]
        if (known?.name != added.name) {
            Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device found: $added" }
        }

        discoveredDevices.update { it + (added.id to added) }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        if (!event.type.contains(Constants.DISCOVERY_SERVICE_TYPE)) return
        val id = DeviceId.parseOrNull(event.name) ?: return
        val removed = discoveredDevices.value[id] ?: return

        Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device lost: $removed" }
        discoveredDevices.update { it - removed.id }
    }
}
