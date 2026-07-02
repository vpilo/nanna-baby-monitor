package org.vpilo.babymonitor.app.cameraselection

import androidx.compose.runtime.Stable
import kotlinx.coroutines.Job
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
import org.vpilo.babymonitor.model.repository.RemoteDiscoveryRepository
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.usecase.GetLocalClientDeviceFlowUseCase

@Stable
class CameraSelectionScreenViewModel(
    private val networkClientRepository: NetworkClientRepository,
    private val settingsRepository: SettingsRepository,
    private val localDiscoveryRepository: LocalDiscoveryRepository,
    private val remoteDiscoveryRepository: RemoteDiscoveryRepository,
    private val deviceStateRepository: DeviceStateRepository,
    getLocalClientDeviceFlowUseCase: GetLocalClientDeviceFlowUseCase,
) : AppViewModel<CameraSelectionScreenAction, CameraSelectionScreenState, CameraSelectionScreenEffect>(
        initialState = CameraSelectionScreenState(),
    ) {
    private var discoveryJob: Job? = null
    private var autoConnectJob: Job? = null

    init {
        discoveryJob =
            getLocalClientDeviceFlowUseCase()
                .onEach { device -> localDiscoveryRepository.register(device) }
                .launchIn(vmScope)
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        discoveryJob = null
        localDiscoveryRepository.unregister()
    }

    override fun SubscriptionScope.onSubscribed() {
        combine(
            remoteDiscoveryRepository.discoveredDevicesFlow,
            localDiscoveryRepository.discoveredDevicesFlow,
        ) { remoteDevices, local ->
            // Filter out remote devices if they are already available in the local network.
            val remoteOnlyDevices =
                remoteDevices.filter { remote ->
                    local.none { remote.id == it.id }
                }
            local + remoteOnlyDevices
        }.subscribe { list ->
            state.copy(availableServers = list).update()
        }

        // When internet connectivity changes, re-enable discovery to ensure the server list is up to date.
        deviceStateRepository.isInternetAvailable
            .subscribe {
                localDiscoveryRepository.refresh()
            }

        networkClientRepository.connectionStateFlow
            .subscribe { netState ->
                state.copy(connectionState = netState).update()
                if (netState is ConnectionState.Connected) {
                    settingsRepository.save(Setting.ClientLastServerId, netState.server.id.toString())
                    CameraSelectionScreenEffect.Connected.sendEffect()
                }
            }

        autoConnectJob =
            vmScope.launch {
                waitForLastConnectedServer()
            }

        settingsRepository.flowOf(Setting.RelayHost).subscribe { host ->
            remoteDiscoveryRepository.setRelayHost(host)
        }
    }

    override suspend fun onUnsubscribed() {
        remoteDiscoveryRepository.setRelayHost()
        autoConnectJob?.cancel()
        autoConnectJob = null
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
                localDiscoveryRepository.discoveredDevicesFlow,
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
