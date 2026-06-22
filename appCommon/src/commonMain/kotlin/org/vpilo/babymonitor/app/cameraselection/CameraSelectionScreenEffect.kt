package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.Device

sealed interface CameraSelectionScreenEffect {
    data class ConnectToLastServer(
        val server: Device.Server,
    ) : CameraSelectionScreenEffect

    object Connected : CameraSelectionScreenEffect
}
