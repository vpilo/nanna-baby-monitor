package org.vpilo.babymonitor.app.client

import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase

class ClientHomeViewModel(
    videoReceiverRepository: StreamingVideoReceiverRepository,
    private val networkClientRepository: NetworkClientRepository,
    private val playReceivedAudio: PlayReceivedAudioUseCase,
) : ViewModel() {

    val frames: Flow<ImageBitmap> = videoReceiverRepository.decodedFrames

    private val _disconnectedEvents = Channel<Unit>(Channel.RENDEZVOUS)
    val disconnectedEvents = _disconnectedEvents.receiveAsFlow()

    private val _state = MutableStateFlow(ClientHomeState())
    val state = _state.asStateFlow()

    init {
        networkClientRepository.stateFlow
            .onEach { netState ->
                Logger.d(TAG) { "Net state updated: $netState" }
                _state.update { it.copy(networkState = netState) }
                if (netState is NetworkState.Disconnected) {
                    _disconnectedEvents.send(Unit)
                }
            }
            .launchIn(viewModelScope)

        playReceivedAudio.isPlaying
            .onEach { playing ->
                _state.update { it.copy(isAudioPlaying = playing) }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ClientHomeAction) {
        when (action) {
            ClientHomeAction.ToggleAudio -> playReceivedAudio.toggle(viewModelScope)
        }
    }

    companion object {
        private val TAG = ClientHomeViewModel::class
    }
}
