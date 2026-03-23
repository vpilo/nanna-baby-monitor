package org.vpilo.babymonitor.app.client

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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.presentation.composables.FpsCounter

private const val TAG = "CameraFeed"

@Composable
fun CameraFeed(
    modifier: Modifier = Modifier,
    frames: Flow<ImageBitmap>,
) {
    var img by remember { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    LifecycleStartEffect(Unit) {
        Logger.d(TAG) { "Started showing feed" }
        val frameJob = scope.launch {
            frames.collect { img = it }
        }

        onStopOrDispose {
            Logger.d(TAG) { "Stopped showing feed" }
            frameJob.cancel()
            img = null
        }
    }

    if (img != null) {
        Box(contentAlignment = Alignment.TopStart) {
            Image(
                bitmap = img!!,
                contentScale = ContentScale.FillWidth,
                contentDescription = null,
                modifier = modifier.fillMaxSize(),
            )
            FpsCounter(frameKey = img!!)
        }
    }
}
