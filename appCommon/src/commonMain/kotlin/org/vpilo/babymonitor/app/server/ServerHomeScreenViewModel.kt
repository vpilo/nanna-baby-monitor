package org.vpilo.babymonitor.app.server

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class ServerHomeScreenViewModel(
    private val server: NetworkServerRepository,
    private val settings: SettingsRepository,
) : AppViewModel<ServerHomeScreenAction, ServerHomeScreenState, Unit>(
    initialState = ServerHomeScreenState(),
) {
    override fun SubscriptionScope.onSubscribed() {
        server.serverStateFlow.subscribe { serverState ->
            state.copy(isAvailable = serverState.isAvailable, captureMode = serverState.captureMode).update()
        }

        vmScope.launch {
            server.start()
        }
    }

    override fun onCleared() {
        vmScope.launch {
            // TODO probably won't be executed if vm is going away
            server.stop()
        }
    }

    override fun onAction(action: ServerHomeScreenAction) {
        when (action) {
            is ServerHomeScreenAction.CaptureModeSelected -> {
                vmScope.launch { server.setCaptureMode(action.captureMode) }
            }
        }
    }
}
