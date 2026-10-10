package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.ktx.firstReachableOrNull
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.settings.model.repository.NetworkAddressCacheRepository

// Paired servers reachable via cached network address.
class GetPairedReachableServersFlowUseCase(
    private val getPairedServersFlowUseCase: GetPairedServersFlowUseCase,
    private val networkAddressCacheRepository: NetworkAddressCacheRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Set<Device.Server>> =
        getPairedServersFlowUseCase()
            .mapLatest { pairedServers ->
                pairedServers.mapNotNull { server ->
                    val addrs = networkAddressCacheRepository.get(server.id)
                        addrs.firstReachableOrNull(Constants.SERVICE_PORT)
                        .also {
                            Logger.d("GetPairedReachableServersFlowUseCase") { "Cached addresses for server ${server.id}: $addrs, selected: $it" } }
                        ?.let { server }
                }.toSet()
            }
}
