package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.ConnectionState

data class CameraSelectionScreenState(
    val connectionState: ConnectionState = ConnectionState.Disconnected(ConnectionState.ErrorReason.NotConnectedYet),
    val connectableServers: List<Device.Server> = emptyList(),
    val pairedServers: List<Device.Server> = emptyList(),
    val newServers: List<Device.Server> = emptyList(),
    val lastConnectedDevice: Device? = null,
    val isRelayConfigured: Boolean = false,
    val isAvailableOnLocalNetwork: Boolean = false,
    val isAvailableOnRelay: Boolean = false,
)
