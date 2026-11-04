package org.vpilo.babymonitor.data

import org.vpilo.babymonitor.model.repository.AudioPlaybackRepository
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

internal expect class DefaultAudioPlaybackRepository(
    settingsRepository: SettingsRepository,
) : AudioPlaybackRepository
