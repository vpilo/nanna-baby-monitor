package org.vpilo.babymonitor.camera.presentation.pairing

import org.vpilo.babymonitor.model.viewmodel.AppViewModel

expect class CameraQrScannerViewModel() : AppViewModel<Unit, Unit, CameraQrScannerEffect>
