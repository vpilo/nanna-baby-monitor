package org.vpilo.babymonitor.app.approlechoice

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.onboarding.OnboardingScreenState
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

class AppRoleChoiceScreenViewModel(
    private val appRoleRepository: AppRoleRepository,
) : AppViewModel<AppRoleChoiceScreenAction, OnboardingScreenState, AppRoleChoiceScreenEffect>(
        initialState = OnboardingScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        vmScope.launch {
            appRoleRepository.chooseRole(AppRole.UNDECIDED)
            appRoleRepository.appRole.subscribe { role ->
                if (role == AppRole.UNDECIDED) return@subscribe
                AppRoleChoiceScreenEffect.RoleChosen(role).sendEffect()
            }
        }
    }

    override fun onAction(action: AppRoleChoiceScreenAction) {
        vmScope.launch {
            when (action) {
                is AppRoleChoiceScreenAction.RoleChosen -> {
                    appRoleRepository.chooseRole(action.appRole)
                }
            }
        }
    }
}
