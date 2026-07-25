package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.PairingRepository

class GetPairedServersFlowUseCase(
    private val pairingRepository: PairingRepository,
) {
    operator fun invoke(): Flow<Set<Device.Server>> =
        pairingRepository.pairedServers
            .map { list ->
                list
                    .map { it.asDevice() }
                    .filterIsInstance<Device.Server>()
                    .toSet()
            }
}
