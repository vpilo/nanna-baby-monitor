package org.vpilo.babymonitor.app.server.pairing

import androidx.compose.runtime.Stable
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

@Stable
class ServerPairingScreenViewModel(
    private val networkServerRepository: NetworkServerRepository,
    private val settings: SettingsRepository,
) : AppViewModel<ServerPairingScreenAction, ServerPairingScreenState, Unit>(
        initialState = ServerPairingScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        settings.flowOf(Setting.DeviceName).subscribe {
            state.copy(serverName = it).update()
        }
        networkServerRepository.pairingState.subscribe { pairingState ->
            state.copy(pairingState = pairingState).update()
        }
        networkServerRepository.startPairingWindow()
    }

    override suspend fun onUnsubscribed() {
        networkServerRepository.cancelPairingWindow()
    }

    override fun onAction(action: ServerPairingScreenAction) {
        when (action) {
            ServerPairingScreenAction.Retry -> networkServerRepository.startPairingWindow()
        }
    }
}
