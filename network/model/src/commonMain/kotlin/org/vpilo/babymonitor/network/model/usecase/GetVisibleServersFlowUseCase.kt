package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.network.model.repository.RemoteDiscoveryRepository

class GetVisibleServersFlowUseCase(
    private val localDiscoveryRepository: LocalDiscoveryRepository,
    private val remoteDiscoveryRepository: RemoteDiscoveryRepository,
) {
    operator fun invoke(): Flow<Set<Device.Server>> =
        combine(
            remoteDiscoveryRepository.discoveredDevicesFlow,
            localDiscoveryRepository.discoveredDevicesFlow,
        ) { remote, local ->
            // Filter out remote devices if they are already available in the local network.
            val remoteOnlyDevices =
                remote.filter { remoteServer ->
                    local.none { remoteServer.id == it.id }
                }
            (local + remoteOnlyDevices).filterIsInstance<Device.Server>().toSet()
        }
}
