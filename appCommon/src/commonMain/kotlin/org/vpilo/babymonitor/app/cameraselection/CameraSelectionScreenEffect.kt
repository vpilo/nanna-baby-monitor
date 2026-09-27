package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.network.model.transport.VersionMismatch

sealed interface CameraSelectionScreenEffect {
    data class ConnectToServer(
        val server: Device.Server,
    ) : CameraSelectionScreenEffect

    object Connected : CameraSelectionScreenEffect

    data class RequirePairing(
        val server: Device.Server,
    ) : CameraSelectionScreenEffect

    class AnnounceConnectionEvent(
        val state: ConnectionState,
    ) : CameraSelectionScreenEffect

    data class AnnounceRelayVersionMismatch(
        val mismatch: VersionMismatch,
    ) : CameraSelectionScreenEffect
}
