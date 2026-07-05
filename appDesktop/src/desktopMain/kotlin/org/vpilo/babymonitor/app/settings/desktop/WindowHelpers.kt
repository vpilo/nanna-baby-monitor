package org.vpilo.babymonitor.app.settings.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import kotlin.math.roundToInt

internal fun DpSize.toSettingValue(): String = "${width.value.roundToInt()}x${height.value.roundToInt()}"

internal fun settingValueToDpSize(value: String): DpSize? {
    val parts = value.split('x')
    if (parts.size != 2) return null
    val width = parts[0].toFloatOrNull() ?: return null
    val height = parts[1].toFloatOrNull() ?: return null
    return DpSize(width.dp, height.dp)
}

internal fun WindowPlacement.toSettingValue(): String = this.name

internal fun settingValueToWindowPlacement(value: String): WindowPlacement? = runCatching { WindowPlacement.valueOf(value) }.getOrNull()

internal fun WindowPosition.toSettingValue(): String = "${x.value.roundToInt()}x${y.value.roundToInt()}"

internal fun settingValueToWindowPosition(value: String): WindowPosition? {
    val parts = value.split('x')
    if (parts.size != 2) return null
    val x = parts[0].toFloatOrNull() ?: return null
    val y = parts[1].toFloatOrNull() ?: return null
    return WindowPosition(x.dp, y.dp)
}
