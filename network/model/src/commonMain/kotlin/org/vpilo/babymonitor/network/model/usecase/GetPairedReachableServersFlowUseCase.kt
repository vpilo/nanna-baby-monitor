package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.ktx.firstReachableOrNull
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.settings.model.repository.NetworkAddressCacheRepository
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

// Paired servers reachable via cached network address.
class GetPairedReachableServersFlowUseCase(
    private val getPairedServersFlowUseCase: GetPairedServersFlowUseCase,
    private val networkAddressCacheRepository: NetworkAddressCacheRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<Set<Device.Server>> =
        getPairedServersFlowUseCase()
            .onStart { emit(emptySet()) }
            .mapLatest { pairedServers ->
                pairedServers.mapNotNull { server ->
                    val addrs: Set<NetworkAddress>
                        measureTimedValue {
                        addrs = networkAddressCacheRepository.get(server.id)
                        addrs.firstReachableOrNull(Constants.SERVICE_PORT)
                            ?.let { server }
                    }
                        .also {
                            Logger.d("GetPairedReachableServersFlowUseCase") { "Cached addresses for server ${server.id}: $addrs, selected: $it in ${it.duration}" }
                        }
                        .value
                }.toSet()
            }
}
