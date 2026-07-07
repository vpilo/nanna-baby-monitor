package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.ConnectionState

sealed interface CameraSelectionScreenEffect {
    data class ConnectToLastServer(
        val server: Device.Server,
    ) : CameraSelectionScreenEffect

    object Connected : CameraSelectionScreenEffect

    class AnnounceConnectionEvent(
        val state: ConnectionState,
    ) : CameraSelectionScreenEffect

    data class RequirePairing(
        val server: Device.Server,
    ) : CameraSelectionScreenEffect
}
