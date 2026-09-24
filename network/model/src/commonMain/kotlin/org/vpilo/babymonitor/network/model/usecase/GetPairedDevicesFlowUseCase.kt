package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository

class GetPairedDevicesFlowUseCase(
    private val pairingStorageRepository: PairingStorageRepository,
) {
    operator fun invoke(): Flow<List<Device>> = pairingStorageRepository.pairedDevices.map { list -> list.map { it.asClient() } }
}
