package org.vpilo.babymonitor.app.cameraselection

import java.net.InetAddress

sealed interface CameraSelectionScreenAction {
    data class ConnectToServer(val address: InetAddress) : CameraSelectionScreenAction
}
