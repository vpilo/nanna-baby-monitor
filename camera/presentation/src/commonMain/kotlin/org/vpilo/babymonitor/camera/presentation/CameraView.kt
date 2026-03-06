package org.vpilo.babymonitor.camera.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.model.CameraImageRotation
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.di.AppRole

private const val TAG = "CameraView"

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    var img by remember { mutableStateOf<ImageBitmap?>(null) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }

    LifecycleResumeEffect(Unit) {
        viewModel.setEnabled(true)
        onPauseOrDispose {
            viewModel.setEnabled(false)
        }
    }

    DisposableEffect(Unit) {
        Logger.d(TAG) { "Started showing preview" }
        val frameJob =
            scope.launch(Dispatchers.Default) {
                viewModel.frames.collect { image ->
                    rotationDegrees = when (image.rotation) {
                        CameraImageRotation.ROTATION_0 -> 0f
                        CameraImageRotation.ROTATION_90 -> 90f
                        CameraImageRotation.ROTATION_180 -> 180f
                        CameraImageRotation.ROTATION_270 -> 270f
                    }
                    img = decodeToImageBitmap(image.data)
                }
            }

        onDispose {
            Logger.d(TAG) { "Stopped showing preview" }
            frameJob.cancel()
        }
    }

    if (img != null) {
        Image(
            img!!,
            contentDescription = null,
            modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotationDegrees },
        )
    }
}
