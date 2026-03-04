package org.vpilo.babymonitor.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object RootNavGraph : Route

    @Serializable
    data object AppRoleChooser : Route

    @Serializable
    data object PermissionCheck : Route

    @Serializable
    data object ClientPreview : Route

    @Serializable
    data object ServerPreview : Route
}
