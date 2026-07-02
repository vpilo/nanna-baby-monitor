package org.vpilo.babymonitor.camera.model.settings

import babymonitor.camera.model.generated.resources.Res
import babymonitor.camera.model.generated.resources.setting_camera_resolution_description
import babymonitor.camera.model.generated.resources.setting_camera_resolution_high
import babymonitor.camera.model.generated.resources.setting_camera_resolution_low
import babymonitor.camera.model.generated.resources.setting_camera_resolution_medium
import babymonitor.camera.model.generated.resources.setting_camera_resolution_title
import babymonitor.camera.model.generated.resources.setting_low_light_boost_description
import babymonitor.camera.model.generated.resources.setting_low_light_boost_title
import babymonitor.camera.model.generated.resources.setting_silence_detection_threshold_description
import babymonitor.camera.model.generated.resources.setting_silence_detection_threshold_title
import org.vpilo.babymonitor.camera.model.CameraResolution
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.makeSetting

val Setting.Companion.CameraResolution: Setting<CameraResolution> by makeSetting {
    Setting.makeEnum(
        id = SettingId("camera_resolution"),
        name = Res.string.setting_camera_resolution_title,
        description = Res.string.setting_camera_resolution_description,
        default = CameraResolution.Medium,
        values =
            mapOf(
                CameraResolution.Low to Res.string.setting_camera_resolution_low,
                CameraResolution.Medium to Res.string.setting_camera_resolution_medium,
                CameraResolution.High to Res.string.setting_camera_resolution_high,
            ),
    )
}

val Setting.Companion.LowLightBoost: Setting<Boolean> by makeSetting {
    Setting.makeBoolean(
        id = SettingId("low_light_boost"),
        name = Res.string.setting_low_light_boost_title,
        description = Res.string.setting_low_light_boost_description,
        default = true,
    )
}

val Setting.Companion.SilenceDetectionThreshold: Setting<Int> by makeSetting {
    Setting.makeInt(
        id = SettingId("silence_detection_threshold"),
        name = Res.string.setting_silence_detection_threshold_title,
        description = Res.string.setting_silence_detection_threshold_description,
        default = MediaFormats.Audio.DEFAULT_NOISE_SENSITIVITY_LEVEL,
        limits =
            IntRange(
                MediaFormats.Audio.MIN_NOISE_SENSITIVITY_LEVEL,
                MediaFormats.Audio.MAX_NOISE_SENSITIVITY_LEVEL,
            ),
    )
}
