package org.vpilo.babymonitor.app.server.home

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.LastCaptureMode
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId
import org.vpilo.babymonitor.settings.model.settings.DeviceName

@Stable
class ServerHomeScreenViewModel(
    private val discoveryManager: LocalDiscoveryRepository,
    private val server: NetworkServerRepository,
    private val settings: SettingsRepository,
    videoCaptureRepository: VideoCaptureRepository,
) : AppViewModel<ServerHomeScreenAction, ServerHomeScreenState, Unit>(
        initialState = ServerHomeScreenState(),
    ) {
    val videoStream: OpaqueVideoStream = videoCaptureRepository.videoStream

    override fun SubscriptionScope.onSubscribed() {
        server.serverStateFlow.subscribe { serverState ->
            state.copy(isAvailable = serverState.isAvailable, captureMode = serverState.captureMode).update()
        }

        combine(
            settings.flowOf(Setting.DeviceId),
            settings.flowOf(Setting.DeviceName),
        ) { rawId, deviceName ->
            val id = checkNotNull(DeviceId.parseOrNull(rawId)) { "Invalid device ID: $rawId" }
            val device = Device.LocalServer(id = id, name = deviceName)
            discoveryManager.register(device)
            device
        }.collectLatest {
            if (!state.isAvailable) {
                server.start(it)
            }
        }

        settings.flowOf(Setting.LastCaptureMode).subscribe {
            state.copy(captureMode = it).update()
            server.setCaptureMode(it)
        }
        // TODO move to usecase
        settings.flowOf(Setting.RelayHost).subscribe { host ->
            server.setRelayHost(host)
        }
    }

    override suspend fun onUnsubscribed() {
        server.stop()
        discoveryManager.unregister()
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
