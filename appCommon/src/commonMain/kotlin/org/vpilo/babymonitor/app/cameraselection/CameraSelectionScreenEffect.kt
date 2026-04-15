package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.repository.ServerId

sealed interface CameraSelectionScreenEffect {
    object Connected : CameraSelectionScreenEffect
}
