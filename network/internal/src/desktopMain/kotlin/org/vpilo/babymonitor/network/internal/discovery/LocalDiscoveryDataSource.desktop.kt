package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import javax.jmdns.JmDNS
import kotlin.coroutines.CoroutineContext

internal actual class LocalDiscoveryDataSource actual constructor(
    private val coroutineContext: CoroutineContext,
) {
    private val discoveryService = JmDNS.create()

    private var listener: DesktopDiscoveryListener? = null

    actual fun startDiscovery(
        device: Device,
        discoveredDevices: MutableStateFlow<Map<DeviceId, Device>>,
    ) {
        check(listener == null) { "Discovery already started" }
        discoveredDevices.update { emptyMap() }
        listener = DesktopDiscoveryListener(coroutineContext, device.id, discoveredDevices)
        discoveryService.addServiceListener(DISCOVERY_DESKTOP_SERVICE_TYPE, listener)
    }

    actual fun stopDiscovery() {
        checkNotNull(listener) { "Discovery not started" }
        discoveryService.removeServiceListener(DISCOVERY_DESKTOP_SERVICE_TYPE, listener)
        listener?.release()
        listener = null
    }

    companion object {
        const val TAG = "LocalDiscoveryDataSource"
    }
}
