package org.vpilo.babymonitor.presentation.client

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.resources.Res
import org.vpilo.babymonitor.presentation.resources.battery_1
import org.vpilo.babymonitor.presentation.resources.battery_2
import org.vpilo.babymonitor.presentation.resources.battery_3
import org.vpilo.babymonitor.presentation.resources.battery_4
import org.vpilo.babymonitor.presentation.resources.battery_5
import org.vpilo.babymonitor.presentation.resources.battery_6
import org.vpilo.babymonitor.presentation.resources.battery_7
import org.vpilo.babymonitor.presentation.resources.client_battery_level
import org.vpilo.babymonitor.presentation.resources.client_battery_level_percent

private val batteryLowLevelColor = Color(0xFF880000)
private val batteryMediumLevelColor = Color(0xFF888800)

@Composable
fun BatteryState(
    modifier: Modifier = Modifier,
    batteryLevel: Int,
) {
    val normalColor = MaterialTheme.colorScheme.onSecondary
    val (drawable, color) =
        @Suppress("MagicNumber")
        when (batteryLevel) {
            in 0..5 -> Res.drawable.battery_1 to batteryLowLevelColor
            in 6..15 -> Res.drawable.battery_2 to batteryLowLevelColor
            in 16..30 -> Res.drawable.battery_3 to batteryMediumLevelColor
            in 31..50 -> Res.drawable.battery_4 to batteryMediumLevelColor
            in 51..70 -> Res.drawable.battery_5 to normalColor
            in 71..90 -> Res.drawable.battery_6 to normalColor
            in 91..100 -> Res.drawable.battery_7 to normalColor
            else -> return
        }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.client_battery_level_percent, batteryLevel),
            color = MaterialTheme.colorScheme.onSecondary,
            style =
                MaterialTheme.typography.bodySmall.copy(
                    color = color,
                    shadow =
                        Shadow(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            blurRadius = Theme.Sizes.Blur.value,
                        ),
                ),
        )
        Spacer(modifier = Modifier.width(Theme.Paddings.Tiny))
        Box {
            Image(
                modifier = modifier.blur(Theme.Sizes.Blur),
                painter = painterResource(drawable),
                contentDescription = null,
                colorFilter = ColorFilter.tint(color = MaterialTheme.colorScheme.secondaryContainer),
            )
            Image(
                painter = painterResource(drawable),
                contentDescription = stringResource(Res.string.client_battery_level),
                colorFilter = ColorFilter.tint(color = color),
            )
        }
    }
}

@Preview
@Composable
private fun BatteryState1Preview() =
    AppPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.onSurface) {
            BatteryState(batteryLevel = 1)
        }
    }

@Preview
@Composable
private fun BatteryState2Preview() =
    AppPreviewTheme {
        Surface(color = MaterialTheme.colorScheme.onSurface) {
            BatteryState(batteryLevel = 10)
        }
    }

@Preview
@Composable
private fun BatteryState3Preview() =
    AppPreviewTheme {
        BatteryState(batteryLevel = 25)
    }

@Preview
@Composable
private fun BatteryState4Preview() =
    AppPreviewTheme {
        BatteryState(batteryLevel = 42)
    }

@Preview
@Composable
private fun BatteryState5Preview() =
    AppPreviewTheme {
        BatteryState(batteryLevel = 69)
    }

@Preview
@Composable
private fun BatteryState6Preview() =
    AppPreviewTheme {
        BatteryState(batteryLevel = 80)
    }

@Preview
@Composable
private fun BatteryState7Preview() =
    AppPreviewTheme {
        BatteryState(batteryLevel = 95)
    }
