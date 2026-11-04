package org.vpilo.babymonitor.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.AudioPlaybackRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.AudioOutputRoute

internal actual class DefaultAudioPlaybackRepository actual constructor(
    private val settingsRepository: SettingsRepository,
) : AudioPlaybackRepository {
    override suspend fun play(input: AudioFrameFlow) {
        settingsRepository.flowOf(Setting.AudioOutputRoute).collectLatest { route ->
            playOnRoute(route, input)
        }
    }

    private suspend fun playOnRoute(
        route: AudioOutputRoute,
        input: AudioFrameFlow,
    ) {
        val channelConfig =
            when (MediaFormats.Audio.CHANNELS) {
                1 -> AudioFormat.CHANNEL_OUT_MONO
                else -> AudioFormat.CHANNEL_OUT_STEREO
            }
        val minBufferSize =
            AudioTrack.getMinBufferSize(
                MediaFormats.Audio.SAMPLE_RATE,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT,
            )

        val isEarpiece = route == AudioOutputRoute.EARPIECE
        val audioTrack =
            AudioTrack
                .Builder()
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(
                            if (isEarpiece) {
                                AudioAttributes.USAGE_VOICE_COMMUNICATION
                            } else {
                                AudioAttributes.USAGE_MEDIA
                            },
                        ).setContentType(
                            if (isEarpiece) {
                                AudioAttributes.CONTENT_TYPE_SPEECH
                            } else {
                                AudioAttributes.CONTENT_TYPE_MUSIC
                            },
                        ).build(),
                ).setAudioFormat(
                    AudioFormat
                        .Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(MediaFormats.Audio.SAMPLE_RATE)
                        .setChannelMask(channelConfig)
                        .build(),
                ).setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

        selectOutputDevice(audioTrack, route)
        audioTrack.play()

        coroutineScope {
            launch {
                Logger.d(TAG) { "Starting audio playback on $route" }
                input.collect { chunk ->
                    if (!isActive) return@collect
                    audioTrack.write(chunk, 0, chunk.size)
                }
            }.invokeOnCompletion {
                Logger.d(TAG) { "Stopping audio playback: $it" }
                audioTrack.stop()
                audioTrack.release()
            }
        }
    }

    private fun selectOutputDevice(
        audioTrack: AudioTrack,
        route: AudioOutputRoute,
    ) {
        val context = GlobalContext.get().get<Context>()
        val audioManager = context.getSystemService(AudioManager::class.java) ?: return
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred =
            when (route) {
                AudioOutputRoute.SPEAKER -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                AudioOutputRoute.EARPIECE -> devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                AudioOutputRoute.HEADSET -> devices.firstOrNull { it.type in HEADSET_TYPES }
            }
        if (preferred != null) {
            val applied = audioTrack.setPreferredDevice(preferred)
            Logger.d(TAG) { "Preferred device ${preferred.productName} type=${preferred.type} applied=$applied" }
        } else {
            Logger.d(TAG) { "No matching output device for $route, leaving routing to the system" }
        }
    }

    private companion object {
        private const val TAG = "DefaultAudioPlaybackRepository"

        private val HEADSET_TYPES =
            intArrayOf(
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_HEARING_AID,
            )
    }
}
