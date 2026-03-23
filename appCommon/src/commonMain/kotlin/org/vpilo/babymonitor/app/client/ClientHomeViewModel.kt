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
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository

class ClientHomeViewModel(
    videoReceiverRepository: StreamingVideoReceiverRepository,
    audioReceiverRepository: StreamingAudioReceiverRepository,
    private val networkClientRepository: NetworkClientRepository,
) : ViewModel() {

    val frames: Flow<ImageBitmap> = videoReceiverRepository.decodedFrames

    val audio: Flow<AudioFrame> = audioReceiverRepository.chunks

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
    }

    private companion object {
        private val TAG = ClientHomeViewModel::class
    }
}
