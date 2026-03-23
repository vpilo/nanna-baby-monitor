package org.vpilo.babymonitor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.navigation.NavigationEvent
import org.vpilo.babymonitor.app.navigation.Route
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository

class AppUiFlowViewModel(
    private val appRoleRepository: AppRoleRepository,
) : ViewModel() {

    private val _navigationEvents = Channel<NavigationEvent>(Channel.RENDEZVOUS)
    val navigationEvents = _navigationEvents.receiveAsFlow()

    fun onAction(action: AppUiFlowAction) {
        when (action) {
            is AppUiFlowAction.RoleChosen -> {
                viewModelScope.launch {
                    appRoleRepository.chooseRole(action.appRole)
                    val destination = when (action.appRole) {
                        AppRole.SERVER -> Route.PermissionCheck
                        AppRole.CLIENT -> Route.ClientConnectionChooser
                        AppRole.UNDECIDED -> error("UNDECIDED role should not be selectable")
                    }
                    _navigationEvents.send(NavigationEvent.NavigateTo(destination))
                }
            }
        }
    }

    private companion object {
        private val TAG = AppUiFlowViewModel::class
    }
}
