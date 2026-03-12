package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Composable FPS counter that updates every time it is recomposed.
 * Not a general purpose FPS counter: only useful to track the updates of the value
 * given as the [frameKey] parameter.
 */
@Composable
fun FpsCounter(frameKey: Any?, modifier: Modifier = Modifier) {
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

    Text(
        text = "%.02f FPS".format(fps),
        modifier = modifier.padding(8.dp),
        color = Color.Green,
    )
}
