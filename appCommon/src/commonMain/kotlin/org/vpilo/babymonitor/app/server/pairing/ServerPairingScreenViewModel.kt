package org.vpilo.babymonitor.app.server.pairing

import androidx.compose.runtime.Stable
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

@Stable
class ServerPairingScreenViewModel(
    private val networkServerRepository: NetworkServerRepository,
) : AppViewModel<ServerPairingScreenAction, ServerPairingScreenState, Unit>(
        initialState = ServerPairingScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
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
