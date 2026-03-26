package org.vpilo.babymonitor.app.clientconnectionchooser

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.AppViewModel
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState

class ClientConnectionChooserViewModel(
    private val networkClientRepository: NetworkClientRepository,
) : AppViewModel<ClientConnectionChooserAction, ClientConnectionChooserState, ClientConnectionChooserEffect>(
    initialState = ClientConnectionChooserState(),
) {
    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.discoveredServers
            .subscribe { list ->
                state.copy(availableServers = list).update()
            }

        networkClientRepository.stateFlow
            .distinctUntilChanged { old, new -> old::class == new::class }
            .subscribe { netState ->
                state.copy(networkState = netState).update()
                if (netState is NetworkState.Connected) {
                    ClientConnectionChooserEffect.Connected(netState.address).sendEffect()
                }
            }
    }

    override fun onAction(action: ClientConnectionChooserAction) {
        when (action) {
            is ClientConnectionChooserAction.ConnectToServer -> {
                vmScope.launch {
                    networkClientRepository.connect(action.address)
                }
            }
        }
    }
}
