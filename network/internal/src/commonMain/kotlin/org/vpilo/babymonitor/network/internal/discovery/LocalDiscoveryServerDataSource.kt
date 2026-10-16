package org.vpilo.babymonitor.network.internal.discovery

import org.vpilo.babymonitor.model.Device
import kotlin.coroutines.CoroutineContext

internal expect class LocalDiscoveryServerDataSource(
    coroutineContext: CoroutineContext,
) {
    fun registerService(device: Device.Server)

    fun unregisterService()
}
