package org.vpilo.babymonitor.app.cameraselection

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState

class CameraSelectionScreenViewModel(
    private val networkClientRepository: NetworkClientRepository,
) : AppViewModel<CameraSelectionScreenAction, CameraSelectionScreenState, CameraSelectionScreenEffect>(
        initialState = CameraSelectionScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.discoveredServersFlow
            .subscribe { list ->
                state.copy(availableServers = list).update()
            }

        networkClientRepository.connectionStateFlow
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
