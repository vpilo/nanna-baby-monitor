package org.vpilo.babymonitor.app.clientpairing

import androidx.compose.runtime.Stable
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.PairingOutcome
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

@Stable
class ClientPairingScreenViewModel(
    private val deviceId: String,
    private val networkClientRepository: NetworkClientRepository,
    private val localDiscoveryRepository: LocalDiscoveryRepository,
) : AppViewModel<ClientPairingScreenAction, ClientPairingScreenState, ClientPairingScreenEffect>(
        initialState = ClientPairingScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        localDiscoveryRepository.discoveredDevicesFlow.subscribe { devices ->
            val targetId = deviceId.toDeviceIdOrNull()
            val server = devices.filterIsInstance<Device.Server>().firstOrNull { it.id == targetId }
            state.copy(server = server).update()
        }
    }

    override fun onAction(action: ClientPairingScreenAction) {
        when (action) {
            is ClientPairingScreenAction.SubmitPin -> submitPin(action.pin)
        }
    }

    private fun submitPin(pin: String) {
        val server = state.server ?: return
        vmScope.launch {
            state.copy(isPairing = true, outcome = null).update()
            val outcome = networkClientRepository.pairWith(server, pin)
            state.copy(isPairing = false, outcome = outcome).update()
            if (outcome is PairingOutcome.Success) {
                ClientPairingScreenEffect.Paired.sendEffect()
            }
        }
    }
}
