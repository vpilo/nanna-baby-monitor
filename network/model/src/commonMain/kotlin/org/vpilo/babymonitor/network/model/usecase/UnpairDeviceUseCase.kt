package org.vpilo.babymonitor.network.model.usecase

import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository

class UnpairDeviceUseCase(
    private val pairingStorageRepository: PairingStorageRepository,
) {
    suspend operator fun invoke(param: DeviceId) = pairingStorageRepository.unpair(param)
}
