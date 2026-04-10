package org.vpilo.babymonitor.app.settings

import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.server_mode_audio_and_video
import babymonitor.appcommon.generated.resources.server_mode_audio_only
import babymonitor.appcommon.generated.resources.server_mode_video_only
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.IsFirstRun by makeSetting {
    Setting.makePrimitive(
        id = SettingId("is_app_first_run"),
        default = true,
    )
}

val Setting.Companion.LastCaptureMode by makeSetting {
    Setting.makeEnum(
        id = SettingId("last_capture_mode"),
        default = CaptureMode.AUDIO_AND_VIDEO,
        values =
            mapOf(
                CaptureMode.VIDEO_ONLY to Res.string.server_mode_video_only,
                CaptureMode.AUDIO_ONLY to Res.string.server_mode_audio_only,
                CaptureMode.AUDIO_AND_VIDEO to Res.string.server_mode_audio_and_video,
            ),
    )
}
