package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.model.repository.ServerState

internal class NetworkControlDataSource {
    private val collector: MutableStateFlow<ServerState> =
        MutableStateFlow(ServerState())

    val serverState: StateFlow<ServerState> = collector.asStateFlow()

    internal suspend fun onServerStateReceived(state: ServerState) {
        collector.value =
            collector.value.copy(
                isAvailable = state.isAvailable,
                captureMode = state.captureMode,
                batteryLevel = state.batteryLevel,
                signalQuality = state.signalQuality,
            )
    }
}
