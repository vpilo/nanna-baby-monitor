package org.vpilo.babymonitor.app.cameraselection

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

class CameraSelectionScreenViewModel(
    private val networkClientRepository: NetworkClientRepository,
    private val settingsRepository: SettingsRepository,
) : AppViewModel<CameraSelectionScreenAction, CameraSelectionScreenState, CameraSelectionScreenEffect>(
        initialState = CameraSelectionScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.discoveredServerIdsFlow
            .subscribe { list ->
                state.copy(availableServers = list).update()
            }

        networkClientRepository.connectionStateFlow
            .subscribe { netState ->
                state.copy(networkState = netState).update()
                if (netState is NetworkState.Connected) {
                    CameraSelectionScreenEffect.Connected.sendEffect()
                }
            }

        settingsRepository.flowOf(Setting.DeviceName).subscribe { name ->
            networkClientRepository.setDeviceName(name)
        }

        settingsRepository.flowOf(Setting.RelayHost).subscribe { host ->
            networkClientRepository.setRelayHost(host)
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
