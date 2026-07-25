package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository

class GetPairedServersFlowUseCase(
    private val pairingStorageRepository: PairingStorageRepository,
) {
    operator fun invoke(): Flow<Set<Device.Server>> =
        pairingStorageRepository.pairedServers
            .map { list ->
                list
                    .map { it.asDevice() }
                    .filterIsInstance<Device.Server>()
                    .toSet()
            }
}
