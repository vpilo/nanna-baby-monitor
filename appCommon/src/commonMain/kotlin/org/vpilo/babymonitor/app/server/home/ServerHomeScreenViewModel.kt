package org.vpilo.babymonitor.app.server.home

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.vpilo.babymonitor.app.settings.LastCaptureMode
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.network.model.RelayConfiguration
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.network.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.network.model.repository.RelayConfigurationRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId
import org.vpilo.babymonitor.settings.model.settings.DeviceName

@Stable
class ServerHomeScreenViewModel(
    private val discoveryManager: LocalDiscoveryRepository,
    private val server: NetworkServerRepository,
    private val settings: SettingsRepository,
    private val relayConfigurationRepository: RelayConfigurationRepository,
    videoCaptureRepository: VideoCaptureRepository,
) : AppViewModel<ServerHomeScreenAction, ServerHomeScreenState, Unit>(
        initialState = ServerHomeScreenState(),
    ) {
    val videoStream: OpaqueVideoStream = videoCaptureRepository.videoStream

    override fun SubscriptionScope.onSubscribed() {
        server.serverStateFlow.subscribe { serverState ->
            state
                .copy(
                    isAvailableOnLocalNetwork = serverState.isAvailableOnLocalNetwork,
                    isAvailableOnRelay = serverState.isAvailableOnRelay,
                    captureMode = serverState.captureMode,
                ).update()
        }

        settings.flowOf(Setting.LastCaptureMode).subscribe {
            state.copy(captureMode = it).update()
            server.setCaptureMode(it)
        }
        relayConfigurationRepository.relayConfiguration.subscribe { configuration ->
            state.copy(isRelayConfigured = configuration.isConfigured).update()
            server.setRelay(configuration)
        }
    }

    init {
        combine(
            settings.flowOf(Setting.DeviceId),
            settings.flowOf(Setting.DeviceName),
        ) { rawId, deviceName ->
            val id = checkNotNull(DeviceId.parseOrNull(rawId)) { "Invalid device ID: $rawId" }
            val device = Device.LocalServer(id = id, name = deviceName)
            discoveryManager.register(device)

            if (!state.isAvailableOnLocalNetwork) {
                server.start(device)
            }

            state.copy(name = deviceName).update()
        }.launchIn(vmScope)
    }

    override fun onCleared() {
        runBlocking {
            discoveryManager.unregister()
            server.setRelay(RelayConfiguration.NONE)
            server.stop()
        }
    }

    override fun onAction(action: ServerHomeScreenAction) {
        when (action) {
            is ServerHomeScreenAction.CaptureModeSelected -> {
                vmScope.launch {
                    settings.save(Setting.LastCaptureMode, action.captureMode)
                    server.setCaptureMode(action.captureMode)
                }
            }
        }
    }
}
