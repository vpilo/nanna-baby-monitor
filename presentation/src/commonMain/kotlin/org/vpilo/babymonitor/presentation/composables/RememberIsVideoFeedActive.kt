package org.vpilo.babymonitor.presentation.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalInspectionMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.vpilo.babymonitor.model.OpaqueVideoStream
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun rememberIsVideoFeedActive(videoStream: OpaqueVideoStream): State<Boolean> {
    if (LocalInspectionMode.current) return rememberUpdatedState(true)
    val isReceivingFrames = remember { mutableStateOf(false) }
    LaunchedEffect(videoStream) {
        var lastCount = videoStream.frameCounter.value
        while (isActive) {
            delay(500.milliseconds)
            val count = videoStream.frameCounter.value
            isReceivingFrames.value = count != lastCount
            lastCount = count
        }
    }
    return isReceivingFrames
}
