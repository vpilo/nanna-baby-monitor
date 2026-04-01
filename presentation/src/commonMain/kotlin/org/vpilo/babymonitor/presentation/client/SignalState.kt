package org.vpilo.babymonitor.presentation.client

import androidx.compose.foundation.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import babymonitor.presentation.generated.resources.Res
import babymonitor.presentation.generated.resources.client_signal_quality
import babymonitor.presentation.generated.resources.signal_1
import babymonitor.presentation.generated.resources.signal_2
import babymonitor.presentation.generated.resources.signal_3
import babymonitor.presentation.generated.resources.signal_4
import babymonitor.presentation.generated.resources.signal_5
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppTheme

private val signalLowQualityColor = Color(0xFF880000)

@Composable
fun SignalState(
    modifier: Modifier = Modifier,
    signalQuality: Int,
) {
    val normalColor = MaterialTheme.colorScheme.onSecondary
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

    Image(
        painter = painterResource(drawable),
        contentDescription = stringResource(Res.string.client_signal_quality),
        colorFilter = ColorFilter.tint(color = color),
        modifier = modifier,
    )
}

@Preview
@Composable
private fun SignalState1Preview() = AppTheme {
    SignalState(signalQuality = 1)
}

@Preview
@Composable
private fun SignalState2Preview() = AppTheme {
    SignalState(signalQuality = 30)
}

@Preview
@Composable
private fun SignalState3Preview() = AppTheme {
    SignalState(signalQuality = 50)
}

@Preview
@Composable
private fun SignalState4Preview() = AppTheme {
    SignalState(signalQuality = 75)
}

@Preview
@Composable
private fun SignalState5Preview() = AppTheme {
    SignalState(signalQuality = 100)
}

