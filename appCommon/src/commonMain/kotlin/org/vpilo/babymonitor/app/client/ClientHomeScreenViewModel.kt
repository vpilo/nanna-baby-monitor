package org.vpilo.babymonitor.app.client

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.vpilo.babymonitor.model.AppViewModel
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase

class ClientHomeScreenViewModel(
    videoReceiverRepository: StreamingVideoReceiverRepository,
    private val networkClientRepository: NetworkClientRepository,
    private val playReceivedAudio: PlayReceivedAudioUseCase,
) : AppViewModel<ClientHomeScreenAction, ClientHomeScreenState, ClientHomeScreenEffect>(
    initialState = ClientHomeScreenState(),
) {
    val frames: Flow<ImageBitmap> = videoReceiverRepository.decodedFrames

    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.stateFlow
            .distinctUntilChanged { old, new -> old::class == new::class }
            .subscribe { netState ->
                state.copy(networkState = netState).update()
                if (netState is NetworkState.Disconnected) {
                    ClientHomeScreenEffect.DisconnectFromServer.sendEffect()
                }
            }

        playReceivedAudio.isPlaying
            .subscribe { playing ->
                state.copy(isAudioPlaying = playing).update()
            }
    }

    override fun onAction(action: ClientHomeScreenAction) {
        when (action) {
            ClientHomeScreenAction.ToggleAudio -> playReceivedAudio.toggle(vmScope)
        }
    }
}
