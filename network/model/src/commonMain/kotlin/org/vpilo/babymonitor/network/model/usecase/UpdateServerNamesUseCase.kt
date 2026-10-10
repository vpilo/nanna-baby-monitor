package org.vpilo.babymonitor.network.model.usecase

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository

class UpdateServerNamesUseCase(
    private val pairingStorageRepository: PairingStorageRepository,
) {
    suspend operator fun invoke(param: Set<Device.Server>) =
        param.forEach {
            pairingStorageRepository.updateName(it.id, it.name)
        }
}
