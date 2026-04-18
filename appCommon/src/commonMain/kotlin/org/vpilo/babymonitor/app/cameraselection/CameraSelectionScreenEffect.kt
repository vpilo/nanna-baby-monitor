package org.vpilo.babymonitor.app.cameraselection

sealed interface CameraSelectionScreenEffect {
    object Connected : CameraSelectionScreenEffect
}
