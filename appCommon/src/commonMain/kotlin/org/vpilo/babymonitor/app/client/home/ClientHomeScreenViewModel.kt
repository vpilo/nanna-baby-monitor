package org.vpilo.babymonitor.app.client.home

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.ClientEnabledAudio
import org.vpilo.babymonitor.app.settings.ClientEnabledVideo
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

class ClientHomeScreenViewModel(
    videoReceiverRepository: StreamingVideoReceiverRepository,
    private val networkClientRepository: NetworkClientRepository,
    private val playReceivedAudio: PlayReceivedAudioUseCase,
    private val settingsRepository: SettingsRepository,
) : AppViewModel<ClientHomeScreenAction, ClientHomeScreenState, Unit>(
        initialState = ClientHomeScreenState(),
    ) {
    val frames: Flow<ImageBitmap> = videoReceiverRepository.decodedFrames

    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.connectionStateFlow
            .subscribe { netState ->
                Logger.d(TAG) { "Network state changed: $netState" }
                state.copy(networkState = netState).update()
            }

        combine(
            playReceivedAudio.isPlaying,
            networkClientRepository.serverStateFlow,
        ) { isPlaying, serverState ->
            Logger.d(TAG) { "Server state changed: $serverState (isPlaying=$isPlaying)" }
            state
                .copy(
                    captureMode = serverState.captureMode,
                    batteryLevel = serverState.batteryLevel,
                    signalQuality = serverState.signalQuality,
                    isAudioPlaying = serverState.isStreamingAudio && isPlaying,
                    isVideoPlaying = serverState.isStreamingVideo,
                ).update()

            // If the server starts streaming audio, but we're not playing, start playing.
            if (!isPlaying && serverState.isStreamingAudio) {
                Logger.d(TAG) { "Starting audio playback from server cue" }
                playReceivedAudio.toggle(vmScope)
            }

            // If the server changes to video only, stop playing audio.
            if (serverState.captureMode == CaptureMode.VIDEO_ONLY && isPlaying) {
                Logger.d(TAG) { "Stopping audio playback from server cue" }
                onAction(ClientHomeScreenAction.ToggleAudio)
            }
        }.collectLatest()

        settingsRepository.flowOf(Setting.ClientEnabledAudio).subscribe {
            Logger.d(TAG) { "Client enabled audio: $it" }
            networkClientRepository.enableAudio(it)
        }

        settingsRepository.flowOf(Setting.ClientEnabledVideo).subscribe {
            Logger.d(TAG) { "Client enabled video: $it" }
            networkClientRepository.enableVideo(it)
        }

        settingsRepository.flowOf(Setting.DeviceName).subscribe { name ->
            networkClientRepository.setDeviceName(name)
        }

        settingsRepository.flowOf(Setting.RelayHost).subscribe { host ->
            networkClientRepository.setRelayHost(host)
        }
    }

    override suspend fun onUnsubscribed() {
        networkClientRepository.disconnect()
    }

    fun disconnect() {
        vmScope.launch {
            networkClientRepository.disconnect()
        }
    }

    override fun onAction(action: ClientHomeScreenAction) {
        when (action) {
            ClientHomeScreenAction.ToggleAudio -> {
                val newState = !state.isAudioPlaying
                networkClientRepository.enableAudio(newState)
                settingsRepository.saveDelayed(Setting.ClientEnabledAudio, newState)
            }

            ClientHomeScreenAction.ToggleVideo -> {
                val newState = !state.isVideoPlaying
                networkClientRepository.enableVideo(newState)
                settingsRepository.saveDelayed(Setting.ClientEnabledVideo, newState)
            }

            ClientHomeScreenAction.Reconnect -> {
                vmScope.launch {
                    networkClientRepository.reconnect()
                }
            }
        }
    }
}
