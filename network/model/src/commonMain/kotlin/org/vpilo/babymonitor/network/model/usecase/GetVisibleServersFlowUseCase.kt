package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.DiscoveredDeviceCacheRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryClientRepository
import org.vpilo.babymonitor.network.model.repository.RemoteDiscoveryRepository

class GetVisibleServersFlowUseCase(
    private val localDiscoveryClientRepository: LocalDiscoveryClientRepository,
    private val discoveredDeviceCacheRepository: DiscoveredDeviceCacheRepository,
    private val remoteDiscoveryRepository: RemoteDiscoveryRepository,
) {
    operator fun invoke(): Flow<Set<Device.Server>> =
        combine(
            remoteDiscoveryRepository.discoveredDevicesFlow,
            localDiscoveryClientRepository.discoveredDevicesFlow,
            discoveredDeviceCacheRepository.cachedDevicesFlow,
        ) { remote, local, cached ->
            val cachedUndiscovered =
                cached
                    .filter { cache -> cache.id !in local.map { it.id } }
                    .toSet()
            val discovered = (local + cachedUndiscovered).toSortedSet()

            // Filter out remote devices if they are already available in the local network.
            val remoteOnlyDevices =
                remote.filter { remoteServer ->
                    local.none { remoteServer.id == it.id }
                }

            (discovered + remoteOnlyDevices).filterIsInstance<Device.Server>().toSortedSet()
        }
}
