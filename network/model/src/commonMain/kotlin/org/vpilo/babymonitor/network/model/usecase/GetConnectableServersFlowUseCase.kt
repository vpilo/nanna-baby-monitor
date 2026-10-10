package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device

class GetConnectableServersFlowUseCase(
    private val getPairedServersFlowUseCase: GetPairedServersFlowUseCase,
    private val getVisibleServersFlowUseCase: GetVisibleServersFlowUseCase,
    private val getPairedReachableServersFlowUseCase: GetPairedReachableServersFlowUseCase,
) {
    operator fun invoke(): Flow<Set<Device.Server>> =
        combine(
            getPairedServersFlowUseCase(),
            getPairedReachableServersFlowUseCase(),
            getVisibleServersFlowUseCase(),
        ) { paired, cached, visible ->
            val pairedIds = paired.map { it.id }
            val connectableViaDiscovery = visible.filter { it.id in pairedIds }.toSet()
            val discoveredIds = connectableViaDiscovery.map { it.id }
            val connectableViaCache = cached.filter { it.id !in discoveredIds }.toSet()

            Logger.i("GetConnectableServersFlowUseCase") {
                    "Cached: ${cached.map { it.id }} | " +
                    "Visible: ${visible.map { it.id }} | " +
                    "Connectable via discovery: ${connectableViaDiscovery.map { it.id }} | " +
                    "Connectable via cache: ${connectableViaCache.map { it.id }}"
            }

            connectableViaDiscovery + connectableViaCache
        }
}
