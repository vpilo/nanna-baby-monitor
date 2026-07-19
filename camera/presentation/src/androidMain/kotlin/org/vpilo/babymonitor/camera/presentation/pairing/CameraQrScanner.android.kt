package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.vpilo.babymonitor.presentation.composables.LoadingIcon

@Composable
actual fun CameraQrScanner(
    modifier: Modifier,
    viewModel: CameraQrScannerViewModel,
    onQrRead: (qr: String) -> Unit,
    onError: () -> Unit,
) {
    @Suppress("UnusedVariable", "UnusedPrivateProperty", "unused")
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        viewModel.bindToCamera(context.applicationContext, lifecycleOwner)
    }

    LaunchedEffect(viewModel) {
        viewModel.effectsFlow.collect { effect ->
            when (effect) {
                is CameraQrScannerEffect.QrScanned -> {
                    onQrRead(effect.qr)
                }

                is CameraQrScannerEffect.CameraError -> {
                    onError()
                }
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(surfaceRequest = request)
        }
            ?: LoadingIcon()
    }
}
