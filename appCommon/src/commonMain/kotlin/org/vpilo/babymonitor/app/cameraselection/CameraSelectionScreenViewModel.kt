package org.vpilo.babymonitor.app.cameraselection

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.ClientLastServerId
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.usecase.GetLocalClientDeviceFlowUseCase

@Stable
class CameraSelectionScreenViewModel(
    private val networkClientRepository: NetworkClientRepository,
    private val settingsRepository: SettingsRepository,
    private val discoveryManager: LocalDiscoveryRepository,
    private val deviceStateRepository: DeviceStateRepository,
    private val getLocalClientDeviceFlowUseCase: GetLocalClientDeviceFlowUseCase,
) : AppViewModel<CameraSelectionScreenAction, CameraSelectionScreenState, CameraSelectionScreenEffect>(
        initialState = CameraSelectionScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        discoveryManager.discoveredDevicesFlow
            .subscribe { list ->
                state.copy(availableServers = list).update()
            }

        // When internet connectivity changes, re-enable discovery to ensure the server list is up to date.
        deviceStateRepository.isInternetAvailable
            .onEach {
                discoveryManager.refresh()
            }.launchIn(vmScope)

        networkClientRepository.connectionStateFlow
            .subscribe { netState ->
                state.copy(connectionState = netState).update()
                if (netState is ConnectionState.Connected) {
                    settingsRepository.save(Setting.ClientLastServerId, netState.server.id.toString())
                    CameraSelectionScreenEffect.Connected.sendEffect()
                }
            }

        vmScope.launch {
            waitForLastConnectedServer()
        }

        getLocalClientDeviceFlowUseCase().subscribe { device ->
            discoveryManager.register(device)
        }

        settingsRepository.flowOf(Setting.RelayHost).subscribe { host ->
            networkClientRepository.setRelayHost(host)
        }
    }

    override suspend fun onUnsubscribed() {
        discoveryManager.unregister()
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

    private suspend fun waitForLastConnectedServer() {
        val lastServer: Device.Server =
            combine(
                networkClientRepository.connectionStateFlow,
                settingsRepository.flowOf(Setting.ClientLastServerId),
                discoveryManager.discoveredDevicesFlow,
            ) { state, rawLastServerId, serverList ->
                val lastServerId = rawLastServerId.toDeviceIdOrNull()
                // Only reconnect on first startup, when we haven't connected yet.
                if (state !is ConnectionState.Disconnected || state.reason != ConnectionState.ErrorReason.NotConnectedYet) {
                    return@combine null
                }
                if (lastServerId == null) return@combine null
                serverList.filterIsInstance<Device.Server>().firstOrNull { it.id == lastServerId }
            }.filterNotNull().first()
        CameraSelectionScreenEffect.ConnectToLastServer(lastServer).sendEffect()
    }
}
