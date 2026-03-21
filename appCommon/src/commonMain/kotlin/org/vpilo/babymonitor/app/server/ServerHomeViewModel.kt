package org.vpilo.babymonitor.app.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import kotlin.time.Duration.Companion.seconds

class ServerHomeViewModel(
    private val server: NetworkServerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServerHomeState())
    val state = _state
        .onStart {
            server.start()

            server.stateFlow
                .onEach {
                    _state.value = ServerHomeState(isAvailable = it)
                }
                .launchIn(viewModelScope)
        }
        .onCompletion {
            server.stop()
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5.seconds),
            _state.value,
        )
}
