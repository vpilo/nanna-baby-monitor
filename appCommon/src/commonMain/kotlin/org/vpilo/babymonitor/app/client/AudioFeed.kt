package org.vpilo.babymonitor.app.client

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.playAudioStream
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.AudioFrameFlow

private const val TAG = "AudioFeed"

@Composable
fun AudioFeed(
    modifier: Modifier = Modifier,
    chunks: AudioFrameFlow,
) {
    val scope = rememberCoroutineScope()

    AudioFeedView(modifier = modifier, coroutineScope = scope, chunks = chunks)
}

@Composable
private fun AudioFeedView(
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    chunks: AudioFrameFlow,
) {
    val isStarted = remember { mutableStateOf(false) }

    LifecycleStartEffect(isStarted.value) {
        val frameJob =
            if (isStarted.value) {
                Logger.d(TAG) { "Playing audio feed" }
                coroutineScope.launch {
                    playAudioStream(chunks)
                }
            } else null

        onStopOrDispose {
            frameJob?.let {
                Logger.d(TAG) { "Stopped audio feed" }
                it.cancel()
            }
        }
    }

    Button(
        modifier = modifier,
        onClick = { isStarted.value = !isStarted.value },
    ) {
        if (isStarted.value) Text("Stop audio feed") else Text("Start audio feed")
    }
}
