package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.ServerState

internal class NetworkControlDataSource {
    private val collector: MutableStateFlow<ServerState> =
        MutableStateFlow(ServerState(isAvailable = false, captureMode = CaptureMode.AUDIO_AND_VIDEO))

    val serverState: StateFlow<ServerState> = collector.asStateFlow()

    internal suspend fun onServerStateReceived(state: ServerState) {
        collector.emit(state)
    }
}
