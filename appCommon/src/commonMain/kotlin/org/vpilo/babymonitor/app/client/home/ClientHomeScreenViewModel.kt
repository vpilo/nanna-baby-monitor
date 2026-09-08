package org.vpilo.babymonitor.app.client.home

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.ClientEnabledAudio
import org.vpilo.babymonitor.app.settings.ClientEnabledVideo
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.network.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

@Stable
class ClientHomeScreenViewModel(
    private val audioReceiverRepository: StreamingAudioReceiverRepository,
    private val videoReceiverRepository: StreamingVideoReceiverRepository,
    private val networkClientRepository: NetworkClientRepository,
    private val settingsRepository: SettingsRepository,
    private val playReceivedAudio: PlayReceivedAudioUseCase,
) : AppViewModel<ClientHomeScreenAction, ClientHomeScreenState, ClientHomeScreenEffect>(
        initialState = ClientHomeScreenState(),
    ) {
    private val isAudioEnabled: Flow<Boolean> =
        combine(
            settingsRepository.flowOf(Setting.ClientEnabledAudio),
            networkClientRepository.serverStateFlow,
        ) { isEnabled, serverState ->
            isEnabled && serverState.isAvailable && serverState.captureMode != CaptureMode.VIDEO_ONLY
        }.distinctUntilChanged()

    private val isVideoEnabled: Flow<Boolean> =
        combine(
            settingsRepository.flowOf(Setting.ClientEnabledVideo),
            networkClientRepository.serverStateFlow,
        ) { isEnabled, serverState ->
            isEnabled && serverState.isAvailable && serverState.captureMode != CaptureMode.AUDIO_ONLY
        }.distinctUntilChanged()

    val videoStreamFlow: Flow<OpaqueVideoStream?> =
        isVideoEnabled
            .map { isEnabled ->
                if (isEnabled) videoReceiverRepository.videoStream else null
            }.distinctUntilChanged()

    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.connectionStateFlow.subscribe { netState ->
            state.copy(connectionState = netState).update()

            if (netState is ConnectionState.Disconnected) {
                ClientHomeScreenEffect.Disconnected.sendEffect()
            }
        }

        combine(
            audioReceiverRepository.isActive,
            videoReceiverRepository.isActive,
        ) { isAudioPlaying, isVideoPlaying ->
            state
                .copy(
                    isAudioPlaying = isAudioPlaying,
                    isVideoPlaying = isVideoPlaying,
                ).update()
        }.subscribe {}

        networkClientRepository.serverStateFlow.subscribe { serverState ->
            state
                .copy(
                    captureMode = serverState.captureMode,
                    batteryLevel = serverState.batteryLevel,
                    signalQuality = serverState.signalQuality,
                ).update()
        }

        isAudioEnabled.subscribe { isEnabled ->
            playReceivedAudio.setPlaying(vmScope, isEnabled)
        }
    }

    override fun onAction(action: ClientHomeScreenAction) {
        vmScope.launch {
            when (action) {
                ClientHomeScreenAction.ToggleAudio -> {
                    settingsRepository.save(Setting.ClientEnabledAudio, !state.isAudioPlaying)
                }

                ClientHomeScreenAction.ToggleVideo -> {
                    settingsRepository.save(Setting.ClientEnabledVideo, !state.isVideoPlaying)
                }

                ClientHomeScreenAction.Disconnect -> {
                    networkClientRepository.disconnect()
                    ClientHomeScreenEffect.Disconnected.sendEffect()
                }
            }
        }
    }
}
