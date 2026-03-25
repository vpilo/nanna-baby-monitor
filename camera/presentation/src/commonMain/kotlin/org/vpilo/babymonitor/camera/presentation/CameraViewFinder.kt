package org.vpilo.babymonitor.camera.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.camera.presentation.ktx.toImageBitmap
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.presentation.composables.FpsCounter

private const val TAG = "CameraViewFinder"

@Composable
fun CameraViewFinder(
    modifier: Modifier = Modifier,
    viewModel: CameraViewFinderViewModel = koinViewModel()
) {
    val scope = rememberCoroutineScope()
    var lastFrame by remember { mutableStateOf<ImageBitmap?>(null) }

    LifecycleStartEffect(Unit) {
        Logger.d(TAG) { "Started showing preview" }
        val frameJob =
            scope.launch {
                viewModel.frames.collect { lastFrame = it.toImageBitmap() }
            }

        onStopOrDispose {
            Logger.d(TAG) { "Stopped showing preview" }
            frameJob.cancel()
            lastFrame = null
        }
    }

    lastFrame?.let { frame ->
        Box(contentAlignment = Alignment.TopStart) {
            Image(
                bitmap = frame,
                contentScale = ContentScale.FillWidth,
                contentDescription = null,
                modifier = modifier.fillMaxSize(),
            )
            FpsCounter(frameKey = frame)
        }
    }
}
