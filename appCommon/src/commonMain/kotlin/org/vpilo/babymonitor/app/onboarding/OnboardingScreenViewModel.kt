package org.vpilo.babymonitor.app.onboarding

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent
import org.vpilo.babymonitor.app.settings.IsFirstRun
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

class OnboardingScreenViewModel(
    private val appRoleRepository: AppRoleRepository,
    private val settingsRepository: SettingsRepository,
) : AppViewModel<Unit, OnboardingScreenState, OnboardingScreenEffect>(initialState = OnboardingScreenState()) {
    override fun SubscriptionScope.onSubscribed() {
        // Ensure that on a new startup the servers are reset, to avoid stale states (eg client's old disconnection state).
        vmScope.launch {
            with(KoinJavaComponent.getKoin()) {
                getOrNull<NetworkServerRepository>()?.stop()
                getOrNull<NetworkClientRepository>()?.reset()
            }
        }

        vmScope.launch {
            combine(
                settingsRepository.flowOf(Setting.IsFirstRun),
                settingsRepository.flowOf(Setting.DeviceName),
                appRoleRepository.appRole,
            ) { isFirstRun, deviceName, role ->
                state.copy(isFirstRun = isFirstRun).update()
                // Ensure that the device has a name, as it's required for the server to be discoverable by clients.
                // The setting validates its value and will make a new name.
                deviceName.ifBlank {
                    settingsRepository.save(Setting.DeviceName, " ")
                }
                if (isFirstRun) {
                    settingsRepository.save(Setting.IsFirstRun, false)
                } else {
                    // Onboarding is not present yet, so just set the role anyway to trigger navigation.
                    OnboardingScreenEffect.SavedRole(role).sendEffect()
                }
            }.collect()
        }
    }
}
