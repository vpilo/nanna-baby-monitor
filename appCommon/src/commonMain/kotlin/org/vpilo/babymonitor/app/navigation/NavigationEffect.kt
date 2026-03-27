package org.vpilo.babymonitor.app.navigation

sealed interface NavigationEffect {
    data class NavigateTo(
        val route: Route,
    ) : NavigationEffect
}
