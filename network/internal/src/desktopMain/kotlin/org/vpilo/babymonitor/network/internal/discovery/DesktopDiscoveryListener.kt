package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.ktx.filterReachable
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.hosts
import org.vpilo.babymonitor.network.internal.discovery.ktx.ranked
import org.vpilo.babymonitor.network.internal.discovery.ktx.toDeviceOrNull
import org.vpilo.babymonitor.network.model.Constants
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

internal class DesktopDiscoveryListener(
    private val coroutineScope: CoroutineScope,
    private val discoveredDevices: MutableStateFlow<Map<DeviceId, Device>>,
    private val onDeviceAddressesUpdated: (DeviceId, Set<NetworkAddress>) -> Unit,
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
        coroutineScope.launch {
            val addresses = event.hosts.filterReachable(Constants.SERVICE_PORT).ranked()
            val address = addresses.firstOrNull()
            if (address == null) {
                Logger.w(DefaultLocalDiscoveryRepository.TAG) { "Device ignored due to no connectable hosts: $event" }
                return@launch
            }
            val added = event.toDeviceOrNull(address) ?: return@launch
            if (added.id == device?.id) return@launch

            onDeviceAddressesUpdated(added.id, addresses)

            // Log only new or renamed devices to reduce verbosity.
            val known = discoveredDevices.value[added.id]
            if (known?.name != added.name) {
                Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device found: $added" }
            }

            discoveredDevices.update { it + (added.id to added) }
            Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Discovered devices now: ${discoveredDevices.value}" }
        }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        if (!event.type.contains(Constants.DISCOVERY_SERVICE_TYPE)) return
        val id = DeviceId.parseOrNull(event.name) ?: return
        val removed = discoveredDevices.value[id] ?: return

        Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device lost: $removed" }
        discoveredDevices.update { it - removed.id }
    }
}
