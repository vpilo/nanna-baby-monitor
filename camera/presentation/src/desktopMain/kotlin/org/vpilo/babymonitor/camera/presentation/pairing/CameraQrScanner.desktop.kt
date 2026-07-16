package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
actual fun CameraQrScanner(
    modifier: Modifier,
    viewModel: CameraQrScannerViewModel,
    onQrRead: (qr: String) -> Unit,
    onError: () -> Unit,
) {
    @Suppress("UnusedVariable", "UnusedPrivateProperty", "unused")
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val cameraFrame by viewModel.frames.collectAsState(ImageBitmap(1, 1))

    LaunchedEffect(viewModel) {
        viewModel.bindToCamera()
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

    Canvas(modifier = modifier.fillMaxSize()) {
        val frame = cameraFrame
        frame ?: run {
            drawRect(Color.Black)
            return@Canvas
        }

        val cropScale =
            maxOf(
                size.width / frame.width.toFloat(),
                size.height / frame.height.toFloat(),
            )

        withTransform(
            {
                clipRect(right = size.width, bottom = size.height)
                scale(scaleX = cropScale, scaleY = cropScale, pivot = Offset.Zero)
            },
        ) {
            drawImage(image = frame)
        }
    }
}
