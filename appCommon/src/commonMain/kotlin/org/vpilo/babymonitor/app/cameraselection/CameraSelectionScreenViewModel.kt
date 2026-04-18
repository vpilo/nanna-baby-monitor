package org.vpilo.babymonitor.app.cameraselection

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

class CameraSelectionScreenViewModel(
    private val networkClientRepository: NetworkClientRepository,
) : AppViewModel<CameraSelectionScreenAction, CameraSelectionScreenState, CameraSelectionScreenEffect>(
        initialState = CameraSelectionScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.localServerIdsFlow
            .subscribe { list ->
                state.copy(localServers = list).update()
            }

        networkClientRepository.relayServerIdsFlow
            .subscribe { list ->
                state.copy(relayServers = list).update()
            }

        networkClientRepository.connectionStateFlow
            .subscribe { netState ->
                state.copy(networkState = netState).update()
                if (netState is NetworkState.Connected) {
                    CameraSelectionScreenEffect.Connected.sendEffect()
                }
            }
    }

    override fun onAction(action: CameraSelectionScreenAction) {
        when (action) {
            is CameraSelectionScreenAction.ConnectToServer -> {
                vmScope.launch {
                    networkClientRepository.connect(action.server)
                }
            }
        }
    }
}
