package org.vpilo.babymonitor.presentation.client

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import babymonitor.presentation.generated.resources.Res
import babymonitor.presentation.generated.resources.battery_1
import babymonitor.presentation.generated.resources.battery_2
import babymonitor.presentation.generated.resources.battery_3
import babymonitor.presentation.generated.resources.battery_4
import babymonitor.presentation.generated.resources.battery_5
import babymonitor.presentation.generated.resources.battery_6
import babymonitor.presentation.generated.resources.battery_7
import babymonitor.presentation.generated.resources.client_battery_level
import babymonitor.presentation.generated.resources.client_battery_level_percent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.presentation.Theme

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
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.width(Theme.Paddings.Tiny))
        Image(
            painter = painterResource(drawable),
            contentDescription = stringResource(Res.string.client_battery_level),
            colorFilter = ColorFilter.tint(color = color),
            modifier = modifier,
        )
    }
}

@Preview
@Composable
private fun BatteryState1Preview() = AppTheme {
    BatteryState(batteryLevel = 1)
}

@Preview
@Composable
private fun BatteryState2Preview() = AppTheme {
    BatteryState(batteryLevel = 10)
}

@Preview
@Composable
private fun BatteryState3Preview() = AppTheme {
    BatteryState(batteryLevel = 25)
}

@Preview
@Composable
private fun BatteryState4Preview() = AppTheme {
    BatteryState(batteryLevel = 42)
}

@Preview
@Composable
private fun BatteryState5Preview() = AppTheme {
    BatteryState(batteryLevel = 69)
}

@Preview
@Composable
private fun BatteryState6Preview() = AppTheme {
    BatteryState(batteryLevel = 80)
}

@Preview
@Composable
private fun BatteryState7Preview() = AppTheme {
    BatteryState(batteryLevel = 95)
}
