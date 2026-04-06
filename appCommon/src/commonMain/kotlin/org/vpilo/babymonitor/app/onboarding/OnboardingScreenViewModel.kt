package org.vpilo.babymonitor.app.onboarding

import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.IsFirstRun
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class OnboardingScreenViewModel(
    private val appRoleRepository: AppRoleRepository,
    private val settingsRepository: SettingsRepository,
) : AppViewModel<OnboardingScreenAction, OnboardingScreenState, OnboardingScreenEffect>(initialState = OnboardingScreenState()) {
    override fun SubscriptionScope.onSubscribed() {
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
}
