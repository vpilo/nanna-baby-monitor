package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.FpsCounter

@Composable
fun ClientPreviewScreenRoot(
    modifier: Modifier = Modifier,
) {
    Column {
        Text(
            text = "Monitor",
            style = MaterialTheme.typography.titleMedium,
            color = Theme.Colors.text,
        )
        Box(modifier = modifier.fillMaxSize()) {
            ReceiverView()
        }
    }
}

private const val TAG = "ReceiverView"

@Composable
fun ReceiverView(
    modifier: Modifier = Modifier,
    videoReceiverRepository: StreamingVideoReceiverRepository = getKoin().get(),
   // audioReceiverRepository: StreamingAudioReceiverRepository = getKoin().get(),
) {
    val scope = rememberCoroutineScope()
    var img by remember { mutableStateOf<ImageBitmap?>(null) }

    LifecycleResumeEffect(Unit) {
        Logger.d(TAG) { "Started showing feed" }

        // Start audio playback
     //   audioReceiverRepository.startPlayback()

        // Collect decoded video frames
        val frameJob = scope.launch(Dispatchers.Default) {
            videoReceiverRepository.decodedFrames.collect { frame ->
                img = frame
            }
        }

        onPauseOrDispose {
            Logger.d(TAG) { "Stopped showing feed" }
            frameJob.cancel()
      //      audioReceiverRepository.stopPlayback()
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
