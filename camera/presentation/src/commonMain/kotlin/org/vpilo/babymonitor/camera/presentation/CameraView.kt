package org.vpilo.babymonitor.camera.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CameraImageRotation

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

        onPauseOrDispose {
            Logger.d(TAG) { "Stopped showing preview" }
            frameJob.cancel()
            viewModel.setEnabled(false)
            img = null
        }
    }

    if (img != null) {
        Box(contentAlignment = Alignment.TopStart) {
            Image(
                bitmap = img!!,
                contentScale = ContentScale.FillWidth,
                contentDescription = null,
                modifier = modifier
                    .fillMaxSize()
                    .also { modifier ->
                        if (rotationDegrees != 0f) {
                            modifier.graphicsLayer { rotationZ = rotationDegrees }
                        }
                    },
            )
            FpsCounter(frameKey = img!!)
        }
    }
}
