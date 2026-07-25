package org.vpilo.babymonitor.app.cameraselection

import androidx.compose.runtime.Stable
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.ClientLastServerId
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.network.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.model.repository.RemoteDiscoveryRepository
import org.vpilo.babymonitor.network.model.usecase.GetConnectableServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetNewServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedNonVisibleServersFlowUseCase
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.usecase.GetLocalClientDeviceFlowUseCase

@Stable
class CameraSelectionScreenViewModel(
    private val deviceId: String? = null,
    private val networkClientRepository: NetworkClientRepository,
    private val settingsRepository: SettingsRepository,
    private val getPairedNonVisibleServersFlowUseCase: GetPairedNonVisibleServersFlowUseCase,
    private val getConnectableServersFlowUseCase: GetConnectableServersFlowUseCase,
    private val getNewServersFlowUseCase: GetNewServersFlowUseCase,
    private val localDiscoveryRepository: LocalDiscoveryRepository,
    private val remoteDiscoveryRepository: RemoteDiscoveryRepository,
    private val deviceStateRepository: DeviceStateRepository,
    private val pairingStorageRepository: PairingStorageRepository,
    getLocalClientDeviceFlowUseCase: GetLocalClientDeviceFlowUseCase,
) : AppViewModel<CameraSelectionScreenAction, CameraSelectionScreenState, CameraSelectionScreenEffect>(
        initialState = CameraSelectionScreenState(),
    ) {
    private var discoveryJob: Job? = null
    private var autoConnectJob: Job? = null

    private var lastAnnouncedEvent: ConnectionState? = null

    init {
        discoveryJob =
            getLocalClientDeviceFlowUseCase()
                .onEach { device -> localDiscoveryRepository.register(device) }
                .launchIn(vmScope)
    }

    override fun onCleared() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    override fun SubscriptionScope.onSubscribed() {
        getConnectableServersFlowUseCase().subscribe {
            state.copy(connectableServers = it.toList()).update()
        }
        getPairedNonVisibleServersFlowUseCase().subscribe {
            state.copy(pairedServers = it.toList()).update()
        }
        getNewServersFlowUseCase().subscribe {
            state.copy(newServers = it.toList()).update()
        }

        // When internet connectivity changes, re-enable discovery to ensure the server list is up to date.
        deviceStateRepository.isInternetAvailable.subscribe {
            localDiscoveryRepository.refresh()
        }

        networkClientRepository.connectionStateFlow.subscribe { netState ->
            state.copy(connectionState = netState).update()
            when (netState) {
                is ConnectionState.Disconnected,
                is ConnectionState.Connecting,
                    -> {
                        if (lastAnnouncedEvent != netState) {
                            lastAnnouncedEvent = netState
                            CameraSelectionScreenEffect.AnnounceConnectionEvent(netState).sendEffect()
                        }
                    }

                is ConnectionState.Connected -> {
                    settingsRepository.save(Setting.ClientLastServerId, netState.server.id.toString())
                    CameraSelectionScreenEffect.Connected.sendEffect()
                }

                else -> {}
            }

            if (netState is ConnectionState.Disconnected) {
                when (netState.reason) {
                    ConnectionState.ErrorReason.ClientQuit,
                    ConnectionState.ErrorReason.PairingRevoked,
                    ConnectionState.ErrorReason.ServerNotFound,
                        -> {
                            Logger.d(TAG) { "Stopping auto-reconnection" }
                            settingsRepository.save(Setting.ClientLastServerId, "")
                        }

                    else -> {
                        // Keep trying to reconnect.
                    }
                }
            }
        }

        autoConnectJob =
            vmScope.launch {
                waitForLastConnectedServer()
            }

        settingsRepository.flowOf(Setting.RelayHost).subscribe { host ->
            remoteDiscoveryRepository.setRelayHost(host)
            state.copy(isRelayConfigured = host.isNotBlank()).update()
        }

        localDiscoveryRepository.isRegisteredFlow.subscribe { isRegistered ->
            state.copy(isAvailableOnLocalNetwork = isRegistered).update()
        }
        remoteDiscoveryRepository.isRegisteredFlow.subscribe { isRegistered ->
            state.copy(isAvailableOnRelay = isRegistered).update()
        }

        if (deviceId != null) {
            vmScope.launch {
                val deviceId = deviceId.toDeviceIdOrNull() ?: return@launch
                val server =
                    getConnectableServersFlowUseCase()
                        .first { it.isNotEmpty() }
                        .firstOrNull { it.id == deviceId }
                        ?: return@launch
                CameraSelectionScreenEffect.ConnectToServer(server).sendEffect()
            }
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
                    if (pairingStorageRepository.findServer(action.server.id) != null) {
                        networkClientRepository.connect(action.server)
                    } else {
                        CameraSelectionScreenEffect.RequirePairing(action.server).sendEffect()
                    }
                }
            }
        }
    }

    private suspend fun waitForLastConnectedServer() {
        settingsRepository
            .flowOf(Setting.ClientLastServerId)
            .collectLatest { rawLastServerId ->
                val lastServerId = rawLastServerId.toDeviceIdOrNull() ?: return@collectLatest

                val serverList = getConnectableServersFlowUseCase().first { it.isNotEmpty() }
                val state = state.connectionState

                // Only reconnect on first startup, when we haven't connected yet.
                if (state !is ConnectionState.Disconnected || state.reason != ConnectionState.ErrorReason.NotConnectedYet) {
                    return@collectLatest
                }
                val lastServer =
                    serverList.firstOrNull { it.id == lastServerId }
                        ?: return@collectLatest
                CameraSelectionScreenEffect.ConnectToServer(lastServer).sendEffect()
            }
    }
}
