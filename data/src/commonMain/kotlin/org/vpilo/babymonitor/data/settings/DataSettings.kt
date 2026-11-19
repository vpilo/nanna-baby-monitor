package org.vpilo.babymonitor.data.settings

import babymonitor.data.generated.resources.Res
import babymonitor.data.generated.resources.settings_audio_output_description
import babymonitor.data.generated.resources.settings_audio_output_earpiece
import babymonitor.data.generated.resources.settings_audio_output_headset
import babymonitor.data.generated.resources.settings_audio_output_speaker
import babymonitor.data.generated.resources.settings_audio_output_title
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.AudioOutputRoute
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.PlatformAvailability
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.AppRole by makeSetting {
    Setting.makeEnum(
        id = SettingId("selected_app_role"),
        default = AppRole.UNDECIDED,
        values = emptyMap(),
    )
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
