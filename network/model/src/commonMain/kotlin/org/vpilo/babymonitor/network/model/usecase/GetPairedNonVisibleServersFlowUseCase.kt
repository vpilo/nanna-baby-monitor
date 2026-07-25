package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.model.Device

class GetPairedNonVisibleServersFlowUseCase(
    private val getPairedServersFlowUseCase: GetPairedServersFlowUseCase,
    private val getVisibleServersFlowUseCase: GetVisibleServersFlowUseCase,
) {
    operator fun invoke(): Flow<Set<Device.Server>> =
        combine(
            getPairedServersFlowUseCase(),
            getVisibleServersFlowUseCase(),
        ) { paired, visible ->
            val visibleIds = visible.map { it.id }
            paired.filter { it.id !in visibleIds }.toSet()
        }
}
