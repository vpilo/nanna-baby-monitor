package org.vpilo.babymonitor.app.cameraselection

import java.net.InetAddress

sealed interface CameraSelectionScreenEffect {
    data class Connected(val address: InetAddress) : CameraSelectionScreenEffect
}
