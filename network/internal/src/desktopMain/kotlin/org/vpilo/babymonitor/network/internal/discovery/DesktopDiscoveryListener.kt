package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.toDeviceOrNull
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlin.collections.emptyMap

internal class DesktopDiscoveryListener : ServiceListener {
    private val _discoveredDevices: MutableStateFlow<Map<DeviceId, Device>> = MutableStateFlow(emptyMap())
    val discoveredDevices: StateFlow<Map<DeviceId, Device>> = _discoveredDevices.asStateFlow()

    private var device: Device? = null

    fun reset(device: Device? = null) {
        this.device = device
        _discoveredDevices.value = emptyMap()
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

        Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device found: $added" }
        _discoveredDevices.update { it + (added.id to added) }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        val removed = event.toDeviceOrNull() ?: return
        if (removed.id !in _discoveredDevices.value) return

        Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device lost: $removed" }
        _discoveredDevices.update { it - removed.id }
    }
}
