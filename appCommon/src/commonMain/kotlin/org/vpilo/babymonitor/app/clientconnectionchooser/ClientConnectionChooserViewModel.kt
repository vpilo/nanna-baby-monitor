package org.vpilo.babymonitor.app.clientconnectionchooser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import kotlin.time.Duration.Companion.seconds

class ClientConnectionChooserViewModel(
    private val networkClientRepository: NetworkClientRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ClientConnectionChooserState())
    val state = _state
        .onStart {
            networkClientRepository.discoveredServers
                .onEach { list ->
                    Logger.d("ClientConnectionChooserViewModel") { "Discovered server list: $list" }
                    _state.update { it.copy(availableServers = list) }
                }
                .launchIn(viewModelScope)

            networkClientRepository.stateFlow
                .onEach { state ->
                    Logger.d("ClientConnectionChooserViewModel") { "Net state updated: $state" }
                    _state.update { it.copy(networkState = state) }
                }
                .launchIn(viewModelScope)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            _state.value,
        )

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
