package org.vpilo.babymonitor.camera.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.camera.model.AudioCaptureRepository
import org.vpilo.babymonitor.camera.model.settings.SilenceDetectionThreshold
import org.vpilo.babymonitor.filters.AudioSilenceFilter
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import kotlin.coroutines.CoroutineContext

class MicrophoneAudioCaptureRepository(
    settingsRepository: SettingsRepository,
    coroutineContext: CoroutineContext,
) : AudioCaptureRepository {
    private val dataSource: AudioCaptureDataSource = AudioCaptureDataSource()

    private val filter = AudioSilenceFilter(input = dataSource.samples)

    override val samples: AudioFrameFlow = filter.output

    init {
        val scope = CoroutineScope(coroutineContext)

        settingsRepository
            .flowOf(Setting.SilenceDetectionThreshold)
            .onEach { filter.sensitivityLevel = it }
            .launchIn(scope)
    }
}
