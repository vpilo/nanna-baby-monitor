package org.vpilo.babymonitor.app.onboarding

sealed interface OnboardingScreenAction {
    object FirstRunDone : OnboardingScreenAction
}
