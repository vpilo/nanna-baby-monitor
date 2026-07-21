package org.vpilo.babymonitor.camera.presentation.pairing

import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import kotlin.coroutines.CoroutineContext

expect class CameraQrScannerViewModel(
    coroutineContext: CoroutineContext,
) : AppViewModel<Unit, Unit, CameraQrScannerEffect>
