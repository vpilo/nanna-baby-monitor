package org.vpilo.babymonitor.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.AppRoleRepository

class AppUiFlowViewModel(
    private val appRoleRepository: AppRoleRepository,
) : ViewModel() {
    fun onAction(action: AppUiFlowAction) {
        when (action) {
            is AppUiFlowAction.RoleChosen -> {
                viewModelScope.launch {
                    appRoleRepository.chooseRole(action.appRole)
                }
            }
        }
    }
}
