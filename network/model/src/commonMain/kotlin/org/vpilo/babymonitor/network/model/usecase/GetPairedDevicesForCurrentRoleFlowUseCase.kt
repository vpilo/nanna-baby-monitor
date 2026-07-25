package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.network.model.repository.PairingRepository

class GetPairedDevicesForCurrentRoleFlowUseCase(
    private val pairingRepository: PairingRepository,
    private val roleRepository: AppRoleRepository,
) {
    operator fun invoke(): Flow<List<Device>> =
        @OptIn(ExperimentalCoroutinesApi::class)
        roleRepository.appRole
            .flatMapLatest { role ->
                when (role) {
                    AppRole.CLIENT -> pairingRepository.pairedServers.map { list -> list.map { it.asDevice() } }
                    AppRole.SERVER -> pairingRepository.pairedClients.map { list -> list.map { it.asDevice() } }
                    AppRole.UNDECIDED -> error("Unknown app role: $role")
                }
            }
}
