package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.repository.NetworkState
import java.net.InetAddress

data class CameraSelectionScreenState(
    val networkState: NetworkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
    val availableServers: Set<InetAddress> = emptySet(),
)
