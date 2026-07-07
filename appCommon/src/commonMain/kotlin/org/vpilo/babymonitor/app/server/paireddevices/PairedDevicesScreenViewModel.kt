package org.vpilo.babymonitor.app.server.paireddevices

import androidx.compose.runtime.Stable
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.toDeviceId
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.repository.PairingRepository

@Stable
class PairedDevicesScreenViewModel(
    private val pairingRepository: PairingRepository,
) : AppViewModel<PairedDevicesScreenAction, PairedDevicesScreenState, Unit>(
        initialState = PairedDevicesScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        pairingRepository.pairedClients.subscribe { clients -> state.copy(clients = clients).update() }
    }

    override fun onAction(action: PairedDevicesScreenAction) {
        when (action) {
            is PairedDevicesScreenAction.Revoke -> {
                vmScope.launch { pairingRepository.revokeClient(action.clientId.toDeviceId()) }
            }
        }
    }
}
