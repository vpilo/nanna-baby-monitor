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
            getVisibleServersFlowUseCase(),
            getPairedReachableServersFlowUseCase(),
        ) { paired, visible, connectableViaCache ->
            val pairedIds = paired.map { it.id }
            val cachedIds = connectableViaCache.map { it.id }
            val connectableViaDiscovery = visible.filter { it.id in pairedIds && it.id !in cachedIds }.toSet()

            fun Device.Server.a(): String =
                if (this is Device.RemoteServer) "rem#$id" else "loc#$id"

            Logger.i("GetConnectableServersFlowUseCase") {
                    "Visible: ${visible.map { it.a() }} | " +
                    "Connectable via discovery: ${connectableViaDiscovery.map { it.a() }} | " +
                    "Connectable via cache: ${connectableViaCache.map { it.a() }}"
            }

            // Cache first: Cached servers are reachable, so they must take precedence over discoverable ones, which include remote ones.
            connectableViaCache + connectableViaDiscovery
        }
}
