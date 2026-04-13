package org.vpilo.babymonitor.app.onboarding

import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_name
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.vpilo.babymonitor.app.settings.IsFirstRun
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

class OnboardingScreenViewModel(
    private val appRoleRepository: AppRoleRepository,
    private val settingsRepository: SettingsRepository,
) : AppViewModel<OnboardingScreenAction, OnboardingScreenState, OnboardingScreenEffect>(initialState = OnboardingScreenState()) {
    override fun SubscriptionScope.onSubscribed() {
        vmScope.launch {
            settingsRepository.flowOf(Setting.DeviceName).collect {
                if (it.isBlank()) {
                    val defaultDeviceName = makeDefaultDeviceName()
                    settingsRepository.save(Setting.DeviceName, defaultDeviceName)
                }
            }
        }

        vmScope.launch {
            combine(
                settingsRepository.flowOf(Setting.IsFirstRun),
                appRoleRepository.appRole,
            ) { isFirstRun, role ->
                state.copy(isFirstRun = isFirstRun).update()
                if (isFirstRun) {
                    settingsRepository.save(Setting.IsFirstRun, false)
                } else {
                    // Onboarding is not present yet, so just set the role anyway to trigger navigation.
                    OnboardingScreenEffect.SavedRole(role).sendEffect()
                }
            }.collect()
        }
    }

    private fun makeDefaultDeviceName(): String {
        val appName =
            runBlocking {
                getString(Res.string.app_name)
            }
        val randomId = (1000..9999).random()
        return "$appName-$randomId"
    }
}
