package org.vpilo.babymonitor.settings.presentation.preview

import babymonitor.settings.presentation.generated.resources.Res
import babymonitor.settings.presentation.generated.resources.example
import babymonitor.settings.presentation.generated.resources.example_mode_audio_and_video
import babymonitor.settings.presentation.generated.resources.example_mode_audio_only
import babymonitor.settings.presentation.generated.resources.example_mode_video_only
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.EnumSetting
import org.vpilo.babymonitor.settings.model.Setting

val testSettingEnum: EnumSetting<CaptureMode> =
    Setting.makeEnum(
        id = SettingId("capture_mode"),
        name = Res.string.example,
        description = Res.string.example,
        default = CaptureMode.AUDIO_AND_VIDEO,
        values =
            mapOf(
                CaptureMode.VIDEO_ONLY to Res.string.example_mode_video_only,
                CaptureMode.AUDIO_ONLY to Res.string.example_mode_audio_only,
                CaptureMode.AUDIO_AND_VIDEO to Res.string.example_mode_audio_and_video,
            ),
    )
