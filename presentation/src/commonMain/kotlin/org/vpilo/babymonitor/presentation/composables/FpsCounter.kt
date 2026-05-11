package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.tooling.preview.Preview
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Composable FPS counter that computes frame time based upon `OpaqueVideoStream.frameCounter`.
 */
@Composable
fun FpsCounter(
    videoStream: OpaqueVideoStream,
    modifier: Modifier = Modifier,
) {
    val frameKey by videoStream.frameCounter.collectAsState()
    var frameCount by remember { mutableIntStateOf(0) }
    var fps by remember { mutableDoubleStateOf(0.0) }
    var lastMark by remember { mutableStateOf(TimeSource.Monotonic.markNow()) }

    key(frameKey) {
        SideEffect {
            frameCount++
            val elapsed = lastMark.elapsedNow()
            if (elapsed > 1.seconds) {
                fps = frameCount * 1_000.0 / elapsed.inWholeMilliseconds
                frameCount = 0
                lastMark = TimeSource.Monotonic.markNow()
            }
        }
    }

    val color =
        when (fps) {
            in 0.0..9.0 -> Color.Red
            in 9.1..15.0 -> Color.Yellow
            else -> Color.Green
        }
    Text(
        text = "%.02f FPS".format(fps),
        modifier =
            modifier
                .padding(Theme.Paddings.Tiny),
        style =
            MaterialTheme.typography.labelSmall.copy(
                color = color,
                shadow =
                    Shadow(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        blurRadius = Theme.Sizes.Blur.value,
                    ),
            ),
    )
}

// @Preview
// @Composable
// private fun FpsCounterPreview() =
//    AppPreviewTheme(useDarkTheme = true) {
//        FpsCounter(
//            modifier = Modifier,
//        )
//    }
