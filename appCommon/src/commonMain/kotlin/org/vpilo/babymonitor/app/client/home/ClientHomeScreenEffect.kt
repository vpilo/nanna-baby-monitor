package org.vpilo.babymonitor.app.client.home

sealed interface ClientHomeScreenEffect {
    data object Disconnected : ClientHomeScreenEffect
}
