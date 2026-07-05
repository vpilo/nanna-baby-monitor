package org.vpilo.babymonitor.presentation.client

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.Tooltip
import org.vpilo.babymonitor.presentation.resources.Res
import org.vpilo.babymonitor.presentation.resources.client_signal_quality
import org.vpilo.babymonitor.presentation.resources.signal_1
import org.vpilo.babymonitor.presentation.resources.signal_2
import org.vpilo.babymonitor.presentation.resources.signal_3
import org.vpilo.babymonitor.presentation.resources.signal_4
import org.vpilo.babymonitor.presentation.resources.signal_5

private val signalLowQualityColor = Color(0xFF880000)

@Composable
fun SignalState(
    modifier: Modifier = Modifier,
    signalQuality: Int,
) {
    val normalColor = MaterialTheme.colorScheme.onSurface
    val (drawable, color) =
        @Suppress("MagicNumber")
        when (signalQuality) {
            in 0..5 -> Res.drawable.signal_1 to signalLowQualityColor
            in 6..35 -> Res.drawable.signal_2 to signalLowQualityColor
            in 36..50 -> Res.drawable.signal_3 to normalColor
            in 51..75 -> Res.drawable.signal_4 to normalColor
            in 76..100 -> Res.drawable.signal_5 to normalColor
            else -> return
        }

    Tooltip(text = stringResource(Res.string.client_signal_quality)) {
        Image(
            modifier = modifier,
            painter = painterResource(drawable),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color = color),
        )
    }
}

@Preview
@Composable
private fun SignalState1Preview() =
    AppPreviewTheme(useDarkTheme = true) {
        Surface {
            SignalState(signalQuality = 1)
        }
    }

@Preview
@Composable
private fun SignalState2Preview() =
    AppPreviewTheme {
        Surface {
            SignalState(signalQuality = 30)
        }
    }

@Preview
@Composable
private fun SignalState3Preview() =
    AppPreviewTheme(useDarkTheme = true) {
        Surface {
            SignalState(signalQuality = 50)
        }
    }

@Preview
@Composable
private fun SignalState4Preview() =
    AppPreviewTheme {
        Surface {
            SignalState(signalQuality = 75)
        }
    }

@Preview
@Composable
private fun SignalState5Preview() =
    AppPreviewTheme(useDarkTheme = true) {
        Surface {
            SignalState(signalQuality = 100)
        }
    }
