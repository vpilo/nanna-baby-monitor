package org.vpilo.babymonitor.app.client.home

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.ClientEnabledAudio
import org.vpilo.babymonitor.app.settings.ClientEnabledVideo
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

class ClientHomeScreenViewModel(
    private val audioReceiverRepository: StreamingAudioReceiverRepository,
    private val videoReceiverRepository: StreamingVideoReceiverRepository,
    private val networkClientRepository: NetworkClientRepository,
    private val playReceivedAudio: PlayReceivedAudioUseCase,
    private val settingsRepository: SettingsRepository,
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

    val framesFlow: Flow<ImageBitmap> =
        @OptIn(ExperimentalCoroutinesApi::class)
        isVideoEnabled.flatMapLatest { open ->
            if (open) videoReceiverRepository.decodedFrames else emptyFlow()
        }

    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.connectionStateFlow.subscribe { netState ->
            Logger.d(TAG) { "Network state changed: $netState" }
            state.copy(connectionState = netState).update()
        }

        combine(
            audioReceiverRepository.isActive,
            videoReceiverRepository.isActive,
            networkClientRepository.serverStateFlow,
        ) { isAudioPlaying, isVideoPlaying, serverState ->
            Logger.d(TAG) {
                "Server state changed: $serverState (audio=$isAudioPlaying, video=$isVideoPlaying)"
            }
            state
                .copy(
                    captureMode = serverState.captureMode,
                    batteryLevel = serverState.batteryLevel,
                    signalQuality = serverState.signalQuality,
                    isAudioPlaying = isAudioPlaying,
                    isVideoPlaying = isVideoPlaying,
                ).update()
        }.collectLatest()

        isAudioEnabled.subscribe { isEnabled ->
            Logger.d(TAG) { "Audio playback enabled: $isEnabled" }
            playReceivedAudio.setPlaying(vmScope, isEnabled)
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
