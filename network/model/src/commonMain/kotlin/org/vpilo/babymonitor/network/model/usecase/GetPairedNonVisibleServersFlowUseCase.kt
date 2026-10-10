package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.model.Device

class GetPairedNonVisibleServersFlowUseCase(
    private val getPairedServersFlowUseCase: GetPairedServersFlowUseCase,
    private val getVisibleServersFlowUseCase: GetVisibleServersFlowUseCase,
    private val getPairedReachableServersFlowUseCase: GetPairedReachableServersFlowUseCase,
) {
    operator fun invoke(): Flow<Set<Device.Server>> =
        combine(
            getPairedServersFlowUseCase(),
            getVisibleServersFlowUseCase(),
            getPairedReachableServersFlowUseCase(),
        ) { paired, visible, reachable ->
            val visibleIds = visible.map { it.id }
            val reachableIds = reachable.map { it.id }
            paired.filter { it.id !in visibleIds && it.id !in reachableIds }.toSet()
        }
}
