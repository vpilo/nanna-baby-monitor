package org.vpilo.babymonitor.app.server

import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_server_home
import babymonitor.appcommon.generated.resources.client_pause_audio
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.PlatformAvailability
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.SettingCategory
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class ServerHomeScreenViewModel(
    private val server: NetworkServerRepository,
    private val settings: SettingsRepository,
) : AppViewModel<ServerHomeScreenAction, ServerHomeScreenState, Unit>(
    initialState = ServerHomeScreenState(),
) {
    override fun SubscriptionScope.onSubscribed() {
        server.serverStateFlow.subscribe { serverState ->
            state.copy(isAvailable = serverState.isAvailable, captureMode = serverState.captureMode).update()
        }

        vmScope.launch {
            server.start()
        }

        val testSetting = Setting(
            id = "test",
            name = Res.string.client_pause_audio,
            description = Res.string.app_title_server_home,
            category = SettingCategory.General,
            platform = PlatformAvailability.AllPlatforms,
            type = String::class,
            default = "default value",
        )
        settings.flowOf(testSetting).subscribe {
        Logger.w(TAG) { "setting changed value: $it" }
        }
        vmScope.launch {
            settings.save(testSetting, "new value")
        }
    }

    override fun onCleared() {
        vmScope.launch {
            // TODO probably won't be executed if vm is going away
            server.stop()
        }
    }

    override fun onAction(action: ServerHomeScreenAction) {
        when (action) {
            is ServerHomeScreenAction.CaptureModeSelected -> {
                vmScope.launch { server.setCaptureMode(action.captureMode) }
            }
        }
    }
}
