package org.vpilo.babymonitor.app

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.navigation.NavigationEffect
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.AppViewModel
import org.vpilo.babymonitor.model.repository.AppRoleRepository

class AppUiFlowViewModel(
    private val appRoleRepository: AppRoleRepository,
) : AppViewModel<AppUiFlowAction, Unit, NavigationEffect>(initialState = Unit) {
    override fun onAction(action: AppUiFlowAction) {
        when (action) {
            is AppUiFlowAction.RoleChosen -> {
                vmScope.launch {
                    appRoleRepository.chooseRole(action.appRole)
                }
                val destination =
                    when (action.appRole) {
                        AppRole.SERVER -> Route.PermissionCheck
                        AppRole.CLIENT -> Route.ClientConnectionChooser
                        AppRole.UNDECIDED -> error("UNDECIDED role should not be selectable")
                    }
                NavigationEffect.NavigateTo(destination).sendEffect()
            }
        }
    }
}
