package org.vpilo.babymonitor.app.cameraselection

import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.AppViewModel
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState

class CameraSelectionScreenViewModel(
    private val networkClientRepository: NetworkClientRepository,
) : AppViewModel<CameraSelectionScreenAction, CameraSelectionScreenState, CameraSelectionScreenEffect>(
        initialState = CameraSelectionScreenState(),
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
                    CameraSelectionScreenEffect.Connected(netState.address).sendEffect()
                }
            }
    }

    override fun onAction(action: CameraSelectionScreenAction) {
        when (action) {
            is CameraSelectionScreenAction.ConnectToServer -> {
                vmScope.launch {
                    networkClientRepository.connect(action.address)
                }
            }
        }
    }
}
