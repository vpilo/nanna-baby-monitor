package org.vpilo.babymonitor.camera.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.camera.model.CameraImageRotation
import org.vpilo.babymonitor.common.Logger

// FIXME on android, request permissions
@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = koinViewModel()
) {
    val isRecording = remember { mutableStateOf(false) }

    Column {
        Spacer(modifier = Modifier.height(50.dp))
        Button(
            {
                isRecording.value = !isRecording.value
                viewModel.setEnabled(isRecording.value)
            },
        ) {
            if (isRecording.value) {
                Text("Stop")
            } else {
                Text("Start")
            }
        }
        if (isRecording.value) {
            Text("Recording")
            Box(modifier = modifier.fillMaxSize()) {
                val scope = rememberCoroutineScope()
                var img by remember { mutableStateOf<ImageBitmap?>(null) }
                var rotationDegrees by remember { mutableFloatStateOf(0f) }

                DisposableEffect(Unit) {
                    Logger.w("CameraView") { "Started showing preview" }
                    val frameJob =
                        scope.launch(Dispatchers.Default) {
                            viewModel.frames.collect { image ->
                                rotationDegrees = when (image.rotation) {
                                    CameraImageRotation.ROTATION_0 -> 0f
                                    CameraImageRotation.ROTATION_90 -> 90f
                                    CameraImageRotation.ROTATION_180 -> 180f
                                    CameraImageRotation.ROTATION_270 -> 270f
                                }
                                img = rgbaToImageBitmap(image.data, image.width, image.height)
                            }
                        }

                    onDispose {
                        Logger.w("CameraView") { "Stopped showing preview" }
                        frameJob.cancel()
                    }
                }

                if (img != null) {
                    Image(
                        img!!,
                        contentDescription = null,
                        modifier.fillMaxSize().graphicsLayer {
                            rotationZ = rotationDegrees
                        }
                    )
                }
            }
        } else {
            Text("Ready")
        }
    }
}
