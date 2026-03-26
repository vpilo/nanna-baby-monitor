package org.vpilo.babymonitor.app.server

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.AppViewModel
import org.vpilo.babymonitor.model.repository.NetworkServerRepository

class ServerHomeViewModel(
    private val server: NetworkServerRepository,
) : AppViewModel<Unit, ServerHomeState, Unit>(
    initialState = ServerHomeState(),
) {
    override fun SubscriptionScope.onSubscribed() {
        server.stateFlow
            .subscribe { isAvailable ->
                state.copy(isAvailable = isAvailable).update()
            }

        vmScope.launch {
            server.start()
        }
    }

    override fun onCleared() {
        super.onCleared()
        server.stop()
    }
}
