package org.vpilo.babymonitor.settings.model.settings

import babymonitor.settings.model.generated.resources.Res
import babymonitor.settings.model.generated.resources.settings_audio_output_description
import babymonitor.settings.model.generated.resources.settings_audio_output_earpiece
import babymonitor.settings.model.generated.resources.settings_audio_output_headset
import babymonitor.settings.model.generated.resources.settings_audio_output_speaker
import babymonitor.settings.model.generated.resources.settings_audio_output_title
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.PlatformAvailability
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

/**
 * Where received monitor audio is played on an Android watching device.
 * The previous build always used voice-call routing, which sticks to the earpiece.
 */
enum class AudioOutputRoute {
    EARPIECE,
    SPEAKER,
    HEADSET,
}

val Setting.Companion.AudioOutputRoute by makeSetting {
    Setting.makeEnum(
        id = SettingId("audio_output_route"),
        name = Res.string.settings_audio_output_title,
        description = Res.string.settings_audio_output_description,
        platform = PlatformAvailability.AndroidOnly,
        default = AudioOutputRoute.SPEAKER,
        values =
            mapOf(
                AudioOutputRoute.EARPIECE to Res.string.settings_audio_output_earpiece,
                AudioOutputRoute.SPEAKER to Res.string.settings_audio_output_speaker,
                AudioOutputRoute.HEADSET to Res.string.settings_audio_output_headset,
            ),
    )
}
