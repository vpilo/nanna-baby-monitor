package org.vpilo.babymonitor.app.clientconnectionchooser

import org.vpilo.babymonitor.model.repository.NetworkState
import java.net.InetAddress

data class ClientConnectionChooserState(
    val networkState: NetworkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
    val availableServers: Set<InetAddress> = emptySet(),
)
