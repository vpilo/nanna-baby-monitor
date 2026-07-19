package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
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
    val cameraFrame by viewModel.frames.collectAsState(ImageBitmap(1, 1))

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

    val frame = cameraFrame
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        frame?.let { frame ->
            Canvas(modifier = Modifier.fillMaxSize()) {
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
            ?: LoadingIcon()
    }
}
