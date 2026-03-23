package org.vpilo.babymonitor.app.navigation

sealed interface NavigationEvent {
    data class NavigateTo(val route: Route) : NavigationEvent
}

