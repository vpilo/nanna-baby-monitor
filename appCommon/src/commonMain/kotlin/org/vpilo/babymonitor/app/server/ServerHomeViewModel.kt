package org.vpilo.babymonitor.app.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.NetworkServerRepository

class ServerHomeViewModel(
    private val server: NetworkServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerHomeState())
    val state = _state.asStateFlow()

    init {
        server.stateFlow
            .onEach { isAvailable ->
                _state.value = ServerHomeState(isAvailable = isAvailable)
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            server.start()
        }
    }

    override fun onCleared() {
        super.onCleared()
        server.stop()
    }

    private companion object {
        private val TAG = ServerHomeViewModel::class
    }
}
