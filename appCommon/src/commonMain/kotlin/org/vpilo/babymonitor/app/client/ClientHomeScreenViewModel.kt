package org.vpilo.babymonitor.app.client

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

class ClientHomeScreenViewModel(
    videoReceiverRepository: StreamingVideoReceiverRepository,
    private val networkClientRepository: NetworkClientRepository,
    private val playReceivedAudio: PlayReceivedAudioUseCase,
) : AppViewModel<ClientHomeScreenAction, ClientHomeScreenState, ClientHomeScreenEffect>(
    initialState = ClientHomeScreenState(),
) {
    val frames: Flow<ImageBitmap> = videoReceiverRepository.decodedFrames

    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.connectionStateFlow
            .distinctUntilChanged { old, new -> old::class == new::class }
            .subscribe { netState ->
                state.copy(networkState = netState).update()
                if (netState is NetworkState.Disconnected) {
                    ClientHomeScreenEffect.DisconnectFromServer.sendEffect()
                }
            }

        networkClientRepository.serverStateFlow.subscribe { serverState ->
            state.copy(captureMode = serverState.captureMode).update()
        }

        playReceivedAudio.isPlaying
            .subscribe { playing ->
                state.copy(isAudioPlaying = playing).update()
            }

        combine(
            playReceivedAudio.isPlaying,
            networkClientRepository.serverStateFlow,
        ) { isPlaying, serverState ->
            if (serverState.captureMode == CaptureMode.VIDEO_ONLY && isPlaying) {
                onAction(ClientHomeScreenAction.ToggleAudio)
            }
        }.collectLatest()
    }

    override fun onAction(action: ClientHomeScreenAction) {
        when (action) {
            ClientHomeScreenAction.ToggleAudio -> playReceivedAudio.toggle(vmScope)
        }
    }
}
