package org.vpilo.babymonitor.app.approlechoice

import org.vpilo.babymonitor.model.AppRole

sealed interface AppRoleChoiceScreenEffect {
    data class RoleChosen(
        val role: AppRole,
    ) : AppRoleChoiceScreenEffect
}
