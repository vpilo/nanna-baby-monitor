package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.camera.model.settings.CameraResolution
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import kotlin.coroutines.CoroutineContext

class CameraVideoCaptureRepository(
    settingsRepository: SettingsRepository,
    coroutineContext: CoroutineContext,
) : VideoCaptureRepository {
    private val dataSource: VideoCaptureDataSource = VideoCaptureDataSource()

    override val frames: CameraFrameFlow = dataSource.frames

    init {
        settingsRepository
            .flowOf(Setting.CameraResolution)
            .onEach { dataSource.setResolution(it) }
            .launchIn(CoroutineScope(coroutineContext))
    }
}
