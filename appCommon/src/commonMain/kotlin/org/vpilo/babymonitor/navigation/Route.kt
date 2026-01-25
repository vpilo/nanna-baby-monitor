package org.vpilo.babymonitor.navigation

import kotlinx.serialization.Serializable

sealed interface Route {

    @Serializable
    data object RootNavGraph : Route

    @Serializable
    data object TestRoute : Route

    @Serializable
    data class BookDetail(val id: String) : Route
}
