package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.Device

sealed interface CameraSelectionScreenAction {
    data class ConnectToServer(
        val server: Device.Server,
    ) : CameraSelectionScreenAction
}
