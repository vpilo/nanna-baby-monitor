package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerId

data class CameraSelectionScreenState(
    val networkState: NetworkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
    val localServers: Set<ServerId> = emptySet(),
    val relayServers: Set<ServerId> = emptySet(),
)
