package org.vpilo.babymonitor.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.vpilo.babymonitor.app.settings.desktop.WindowPlacement
import org.vpilo.babymonitor.app.settings.desktop.WindowPosition
import org.vpilo.babymonitor.app.settings.desktop.WindowSize
import org.vpilo.babymonitor.app.settings.desktop.settingValueToDpSize
import org.vpilo.babymonitor.app.settings.desktop.settingValueToWindowPlacement
import org.vpilo.babymonitor.app.settings.desktop.settingValueToWindowPosition
import org.vpilo.babymonitor.app.settings.desktop.toSettingValue
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import java.awt.GraphicsEnvironment
import java.awt.Point
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private val UPDATE_DEBOUNCE_DELAY = 1.seconds

// Restoring window state is stupidly complex. Different screens may have different densities, and there is a creation time bug: AI says
// > Window position/size are AWT "user space" units, identical to the coordinate space of GraphicsConfiguration.bounds (verified
// > against Compose Desktop sources: setSizeImpl and setPositionImpl place/size windows directly in this space, with no scale
// > conversion). Size, however, is realized in physical device pixels at window-creation time, and on Wayland the GraphicsConfiguration
// > scale can be transiently wrong then (e.g. 2.0 settling to 1.0 a couple seconds later), doubling the reported dp size.
// This implementation is probably still wrong: to be improved.
@Composable
internal fun rememberPersistedWindowState(settingsRepository: SettingsRepository): WindowState {
    data class WindowStateHolder(
        override var isMinimized: Boolean,
        override var placement: WindowPlacement,
        override var position: WindowPosition,
        override var size: DpSize,
    ) : WindowState

    val initialState =
        remember {
            runBlocking {
                val position = settingValueToWindowPosition(settingsRepository.load(Setting.WindowPosition))
                val scale = screenScaleForPosition(position)
                WindowStateHolder(
                    size =
                        settingValueToDpSize(settingsRepository.load(Setting.WindowSize))
                            ?.let { DpSize(it.width / scale, it.height / scale) }
                            ?: DpSize(800.dp, 800.dp),
                    placement =
                        settingValueToWindowPlacement(settingsRepository.load(Setting.WindowPlacement))
                            ?: WindowPlacement.Floating,
                    position = position?.let { WindowPosition(it.x / scale, it.y / scale) } ?: WindowPosition.PlatformDefault,
                    isMinimized = false,
                )
            }
        }

    val windowState =
        rememberWindowState(
            placement = initialState.placement,
            size = initialState.size,
            position = initialState.position,
            isMinimized = initialState.isMinimized,
        )

    @OptIn(FlowPreview::class)
    LaunchedEffect(windowState) {
        delay(UPDATE_DEBOUNCE_DELAY)

        snapshotFlow { windowState.placement }
            .debounce(UPDATE_DEBOUNCE_DELAY)
            .onEach { settingsRepository.saveDelayed(Setting.WindowPlacement, it.toSettingValue()) }
            .launchIn(this)

        snapshotFlow { windowState.size }
            .debounce(UPDATE_DEBOUNCE_DELAY)
            .onEach {
                if (windowState.placement != WindowPlacement.Floating) return@onEach
                settingsRepository.saveDelayed(
                    Setting.WindowSize,
                    DpSize(it.width, it.height).toSettingValue(),
                )
            }.launchIn(this)

        snapshotFlow { windowState.position }
            .debounce(UPDATE_DEBOUNCE_DELAY)
            .onEach {
                if (windowState.placement != WindowPlacement.Floating) return@onEach
                settingsRepository.saveDelayed(Setting.WindowPosition, it.toSettingValue())
            }.launchIn(this)
    }

    return windowState
}

private fun defaultDisplayScale(): Float =
    GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .defaultScreenDevice
        .defaultConfiguration
        .defaultTransform
        .scaleX
        .toFloat()

private fun screenScaleForPosition(position: WindowPosition?): Float {
    val absolute = position as? WindowPosition.Absolute ?: return defaultDisplayScale()
    val point = Point(absolute.x.value.roundToInt(), absolute.y.value.roundToInt())
    val device =
        GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .screenDevices
            .firstOrNull { it.defaultConfiguration.bounds.contains(point) }
            ?: return defaultDisplayScale()
    return device.defaultConfiguration.defaultTransform.scaleX
        .toFloat()
}
