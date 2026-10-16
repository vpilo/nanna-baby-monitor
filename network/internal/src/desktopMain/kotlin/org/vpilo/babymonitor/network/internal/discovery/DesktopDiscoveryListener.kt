package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.ktx.filterReachable
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.hosts
import org.vpilo.babymonitor.network.internal.discovery.ktx.ranked
import org.vpilo.babymonitor.network.internal.discovery.ktx.toDeviceOrNull
import org.vpilo.babymonitor.network.model.Constants
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlin.coroutines.CoroutineContext

internal class DesktopDiscoveryListener(
    coroutineContext: CoroutineContext,
    private val localId: DeviceId,
    private val discoveredDevices: MutableStateFlow<Map<DeviceId, Device>>,
) : ServiceListener {
    private val coroutineScope = CoroutineScope(coroutineContext + SupervisorJob())

    fun release() {
        coroutineScope.cancel()
    }

    override fun serviceAdded(event: ServiceEvent) {
        event.dns.requestServiceInfo(event.type, event.name)
    }

    override fun serviceResolved(event: ServiceEvent) {
        coroutineScope.launch {
            val addresses = event.hosts.filterReachable(Constants.SERVICE_PORT).ranked()
            if (addresses.isEmpty()) return@launch

            val added = event.toDeviceOrNull(addresses) ?: return@launch
            if (added.id == localId) return@launch

            discoveredDevices.value[added.id]
                ?.let { return@launch }
                ?: run {
                    Logger.i(LocalDiscoveryDataSource.TAG) { "Device found: $added" }
                }

            discoveredDevices.update { it + (added.id to added) }
        }
    }

    override fun serviceRemoved(event: ServiceEvent) {
        if (!event.type.contains(Constants.DISCOVERY_SERVICE_TYPE)) return
        val id = DeviceId.parseOrNull(event.name) ?: return
        val removed = discoveredDevices.value[id] ?: return

        Logger.i(LocalDiscoveryDataSource.TAG) { "Device lost: $removed" }
        discoveredDevices.update { it - removed.id }
    }
}
