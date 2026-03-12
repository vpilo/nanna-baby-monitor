package org.vpilo.babymonitor.app.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.getKoin
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.StreamingVideoRepository
import org.vpilo.babymonitor.presentation.Theme

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
    videoCaptureRepository: StreamingVideoRepository = getKoin().get(),
) {
    val scope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        Logger.d(TAG) { "Started showing feed" }
        val frameJob =
            scope.launch(Dispatchers.Default) {
                videoCaptureRepository.chunks.collect {
                    Logger.d(TAG) { "frame: ${it.data.size} bytes, keyframe: ${it.isKeyFrame}, config: ${it.isCodecConfig}" }
                }
            }

        onPauseOrDispose {
            Logger.d(TAG) { "Stopped showing feed" }
            frameJob.cancel()
        }
    }
}
