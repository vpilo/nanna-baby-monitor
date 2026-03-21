package org.vpilo.babymonitor.app.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object RootNavGraph : Route

    @Serializable
    data object AppRoleChooser : Route

    @Serializable
    data object PermissionCheck : Route

    @Serializable
    data object ClientConnectionChooser : Route
    @Serializable
    data object ClientHome : Route

    @Serializable
    data object ServerHome : Route
}
