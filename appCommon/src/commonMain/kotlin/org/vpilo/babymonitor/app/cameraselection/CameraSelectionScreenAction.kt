package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.repository.ServerId

sealed interface CameraSelectionScreenAction {
    data class ConnectToServer(
        val server: ServerId,
    ) : CameraSelectionScreenAction
}
