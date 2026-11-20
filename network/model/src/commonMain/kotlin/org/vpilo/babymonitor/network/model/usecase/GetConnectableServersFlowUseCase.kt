package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.vpilo.babymonitor.model.Device

class GetConnectableServersFlowUseCase(
    private val getPairedServersFlowUseCase: GetPairedServersFlowUseCase,
    private val getVisibleServersFlowUseCase: GetVisibleServersFlowUseCase,
) {
    operator fun invoke(includeCached: Boolean = true): Flow<Set<Device.Server>> =
        combine(
            getPairedServersFlowUseCase(),
            getVisibleServersFlowUseCase(includeCached = includeCached),
        ) { paired, visible ->
            val pairedIds = paired.map { it.id }
            visible.filter { it.id in pairedIds }.toSet()
        }
}
