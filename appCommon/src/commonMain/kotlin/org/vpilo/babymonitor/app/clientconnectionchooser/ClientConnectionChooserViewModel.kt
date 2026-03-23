package org.vpilo.babymonitor.app.clientconnectionchooser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import java.net.InetAddress

class ClientConnectionChooserViewModel(
    private val networkClientRepository: NetworkClientRepository,
) : ViewModel() {

    companion object {
        private val TAG = ClientConnectionChooserViewModel::class
    }

    private val _connectedEvents = Channel<InetAddress>(Channel.RENDEZVOUS)
    val connectedEvents = _connectedEvents.receiveAsFlow()

    private val _state = MutableStateFlow(ClientConnectionChooserState())
    val state = _state.asStateFlow()

    init {
        networkClientRepository.discoveredServers
            .onEach { list ->
                Logger.d(TAG) { "Discovered server list: $list" }
                _state.update { it.copy(availableServers = list) }
            }
            .launchIn(viewModelScope)

        networkClientRepository.stateFlow
            .onEach { netState ->
                Logger.d(TAG) { "Net state updated: $netState" }
                _state.update { it.copy(networkState = netState) }
                if (netState is NetworkState.Connected) {
                    _connectedEvents.send(netState.address)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onAction(action: ClientConnectionChooserAction) {
        when (action) {
            is ClientConnectionChooserAction.ConnectToServer -> {
                viewModelScope.launch {
                    networkClientRepository.connect(action.address)
                }
            }
        }
    }
}
