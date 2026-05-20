package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.ServerId

data class CameraSelectionScreenState(
    val connectionState: ConnectionState = ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet),
    val availableServers: Set<ServerId> = emptySet(),
    val lastConnectedServerId: ServerId? = null,
)
