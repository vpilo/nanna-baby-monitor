package org.vpilo.babymonitor.app.navigation

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable
    data object RootNavGraph : Route

    @Serializable
    data object Onboarding : Route

    @Serializable
    data object AppRoleChooser : Route

    @Serializable
    data object Menu : Route

    @Serializable
    data object Quit : Route

    @Serializable
    data object PermissionCheck : Route

    @Serializable
    data object CameraSelection : Route

    @Serializable
    data object ClientHome : Route

    @Serializable
    data object ServerHome : Route
}
