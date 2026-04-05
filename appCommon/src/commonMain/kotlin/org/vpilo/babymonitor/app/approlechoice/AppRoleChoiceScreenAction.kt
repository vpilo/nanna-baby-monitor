package org.vpilo.babymonitor.app.approlechoice

import org.vpilo.babymonitor.model.AppRole

sealed interface AppRoleChoiceScreenAction {
    data class RoleChosen(
        val appRole: AppRole,
    ) : AppRoleChoiceScreenAction
}
