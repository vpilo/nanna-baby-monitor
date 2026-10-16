package org.vpilo.babymonitor.network.internal.discovery

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.internal.discovery.ktx.toAttributes
import org.vpilo.babymonitor.network.model.Constants
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.coroutines.CoroutineContext

internal actual class LocalDiscoveryServerDataSource actual constructor(
    coroutineContext: CoroutineContext,
) {
    private val discoveryService = JmDNS.create()

    actual fun registerService(device: Device.Server) {
        discoveryService.registerService(createServiceInfo(device))
    }

    actual fun unregisterService() {
        discoveryService.unregisterAllServices()
    }

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
