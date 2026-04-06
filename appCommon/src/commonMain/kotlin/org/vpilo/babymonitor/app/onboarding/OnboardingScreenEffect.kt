package org.vpilo.babymonitor.app.onboarding

import org.vpilo.babymonitor.model.AppRole

sealed interface OnboardingScreenEffect {
    data class SavedRole(
        val role: AppRole,
    ) : OnboardingScreenEffect
}
