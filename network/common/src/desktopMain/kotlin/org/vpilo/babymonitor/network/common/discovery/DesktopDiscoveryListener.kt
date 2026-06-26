package org.vpilo.babymonitor.network.common.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.common.discovery.ktx.toDeviceOrNull
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

internal class DesktopDiscoveryListener : ServiceListener {
    private val _discoveredDevices: MutableStateFlow<Set<Device>> = MutableStateFlow(emptySet())
    val discoveredDevices: StateFlow<Set<Device>> = _discoveredDevices.asStateFlow()

    fun reset() {
        _discoveredDevices.value = emptySet()
    }

    override fun serviceAdded(event: ServiceEvent) {
        event.dns.requestServiceInfo(event.type, event.name)
    }

    override fun serviceResolved(event: ServiceEvent) {
        val added = event.toDeviceOrNull() ?: return
        if (added in _discoveredDevices.value) return

        if (added.addresses.isEmpty()) {
            Logger.w(DefaultLocalDiscoveryRepository.TAG) { "Device resolved with no hosts: $added" }
            return
        }

        Logger.i(DefaultLocalDiscoveryRepository.TAG) {
            if (_discoveredDevices.value.any { it.id == added.id }) {
                "Device updated: $added"
            } else {
                "Device found: $added"
            }
        }
        _discoveredDevices.update { devices -> devices.filterNot { it.id == added.id }.plus(added).toSet() }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        val removed = event.toDeviceOrNull() ?: return

        if (removed !in _discoveredDevices.value) return

        Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device lost: $removed" }
        _discoveredDevices.update { devices -> devices.filterNot { it.id == removed.id }.toSet() }
    }
}
