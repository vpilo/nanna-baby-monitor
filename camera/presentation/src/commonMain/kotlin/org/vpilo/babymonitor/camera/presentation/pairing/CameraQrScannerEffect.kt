package org.vpilo.babymonitor.camera.presentation.pairing

sealed interface CameraQrScannerEffect {
    data class QrScanned(
        val qr: String,
    ) : CameraQrScannerEffect

    object CameraError : CameraQrScannerEffect
}
