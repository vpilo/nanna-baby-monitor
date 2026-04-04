package org.vpilo.babymonitor.app.approlechoice

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.IsFirstRun
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class AppRoleChoiceScreenViewModel(
    private val appRoleRepository: AppRoleRepository,
    private val settingsRepository: SettingsRepository,
) : AppViewModel<AppRoleChoiceScreenAction, AppRoleChoiceScreenState, AppRoleChoiceScreenEffect>(
    initialState = AppRoleChoiceScreenState(),
) {
    override fun SubscriptionScope.onSubscribed() {
        settingsRepository.flowOf(Setting.IsFirstRun).subscribe { isFirstRun ->
            state.copy(isFirstRun = isFirstRun).update()
        }
        appRoleRepository.appRole.subscribe { role ->
            if (role == AppRole.UNDECIDED) return@subscribe
            AppRoleChoiceScreenEffect.RoleChosen(role).sendEffect()
        }
    }

    override fun onAction(action: AppRoleChoiceScreenAction) {
        when (action) {
            is AppRoleChoiceScreenAction.FirstRunDone -> {
                vmScope.launch {
                    settingsRepository.save(Setting.IsFirstRun, true)
                }
                state.copy(isFirstRun = false).update()
            }

            is AppRoleChoiceScreenAction.RoleChosen -> {
                vmScope.launch {
                    appRoleRepository.chooseRole(action.appRole)
                    AppRoleChoiceScreenEffect.RoleChosen(action.appRole).sendEffect()
                }
            }
        }
    }
}
