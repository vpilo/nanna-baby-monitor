package org.vpilo.babymonitor.app

import org.vpilo.babymonitor.model.AppRole

sealed interface AppUiFlowAction {

    data class RoleChosen(val appRole: AppRole): AppUiFlowAction

}
