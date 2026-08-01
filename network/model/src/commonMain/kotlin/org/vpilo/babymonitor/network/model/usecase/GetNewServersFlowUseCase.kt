package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.model.Device

/**
 * Returns a flow of new (unpaired) servers that are visible on the local network.
 * Remote servers are excluded, as they cannot be paired with.
 */
class GetNewServersFlowUseCase(
    private val getPairedServersFlowUseCase: GetPairedServersFlowUseCase,
    private val getVisibleServersFlowUseCase: GetVisibleServersFlowUseCase,
) {
    operator fun invoke(): Flow<Set<Device.Server>> =
        combine(
            getPairedServersFlowUseCase(),
            getVisibleServersFlowUseCase(),
        ) { paired, visible ->
            val pairedIds = paired.map { it.id }
            visible.filter { it !is Device.RemoteServer && it.id !in pairedIds }.toSet()
        }
}
