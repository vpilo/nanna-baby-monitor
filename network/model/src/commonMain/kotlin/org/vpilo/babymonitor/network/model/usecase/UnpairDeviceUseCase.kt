package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.first
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.repository.PairingRepository

class UnpairDeviceUseCase(
    private val pairingRepository: PairingRepository,
    private val roleRepository: AppRoleRepository,
) {
    suspend operator fun invoke(param: DeviceId) {
        when (roleRepository.appRole.first()) {
            AppRole.CLIENT -> pairingRepository.unpairServer(param)
            AppRole.SERVER -> pairingRepository.revokeClient(param)
            AppRole.UNDECIDED -> error("Unspecified app role")
        }
    }
}
