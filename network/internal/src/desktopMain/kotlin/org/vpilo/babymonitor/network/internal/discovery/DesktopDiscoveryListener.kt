package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.toDeviceOrNull
import org.vpilo.babymonitor.network.model.Constants
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlin.time.Clock
import kotlin.time.Instant

internal class DesktopDiscoveryListener : ServiceListener {
    private val _discoveredDevices: MutableStateFlow<Map<DeviceId, Device>> = MutableStateFlow(emptyMap())
    val discoveredDevices: StateFlow<Map<DeviceId, Device>> = _discoveredDevices.asStateFlow()
    private val lastUpdateInstants: MutableMap<DeviceId, Instant> = mutableMapOf()

    private var device: Device? = null

    fun reset(device: Device? = null) {
        this.device = device
        _discoveredDevices.value = emptyMap()
        lastUpdateInstants.clear()
    }

    fun trim() {
        val now = Clock.System.now()
        val updated =
            _discoveredDevices.value.filterKeys { id ->
                lastUpdateInstants[id]
                    ?.let { lastSeen -> now - lastSeen > Constants.DISCOVERY_TRIM_PERIOD }
                    ?: false
            }
        updated.forEach { (_, removed) ->
            Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device timed out: $removed" }
            _discoveredDevices.update { it - removed.id }
        }
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
        _discoveredDevices.value[added.id]
            ?.takeIf { it.name != added.name }
            ?.let {
                Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device found: $added" }
            }

        _discoveredDevices.update { it + (added.id to added) }
        lastUpdateInstants[added.id] = Clock.System.now()
    }

    override fun serviceRemoved(event: ServiceEvent) {
        if (!event.type.contains(Constants.DISCOVERY_SERVICE_TYPE)) return
        val id = DeviceId.parseOrNull(event.name) ?: return
        val removed = _discoveredDevices.value[id] ?: return

        Logger.i(DefaultLocalDiscoveryRepository.TAG) { "Device lost: $removed" }
        _discoveredDevices.update { it - removed.id }
    }
}
