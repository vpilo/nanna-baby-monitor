package org.vpilo.babymonitor.network.server

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.network.model.ServerState

internal class ServerStateDataSource {
    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    fun update(block: (ServerState) -> ServerState) {
        _state.value = block(_state.value)
    }
}
