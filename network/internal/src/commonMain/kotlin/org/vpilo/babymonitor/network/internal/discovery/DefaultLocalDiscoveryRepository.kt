package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.settings.model.repository.NetworkAddressCacheRepository
import kotlin.coroutines.CoroutineContext

internal expect class DefaultLocalDiscoveryRepository(
    coroutineContext: CoroutineContext,
    networkAddressCacheRepository: NetworkAddressCacheRepository,
) : LocalDiscoveryRepository {
    override val discoveredDevicesFlow: Flow<Set<Device>>

    override val isRegisteredFlow: Flow<Boolean>

    override fun register(device: Device)

    override fun unregister()

    override fun refresh()
}
