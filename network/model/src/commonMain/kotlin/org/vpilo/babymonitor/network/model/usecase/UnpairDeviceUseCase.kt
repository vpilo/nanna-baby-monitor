package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.first
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository

class UnpairDeviceUseCase(
    private val pairingStorageRepository: PairingStorageRepository,
    private val roleRepository: AppRoleRepository,
) {
    suspend operator fun invoke(param: DeviceId) {
        when (roleRepository.appRole.first()) {
            AppRole.CLIENT -> pairingStorageRepository.unpairServer(param)
            AppRole.SERVER -> pairingStorageRepository.revokeClient(param)
            AppRole.UNDECIDED -> error("Unspecified app role")
        }
    }
}
