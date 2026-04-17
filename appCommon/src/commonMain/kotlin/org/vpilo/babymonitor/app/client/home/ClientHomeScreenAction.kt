package org.vpilo.babymonitor.app.client.home

sealed interface ClientHomeScreenAction {
    data object ToggleAudio : ClientHomeScreenAction

    data object ToggleVideo : ClientHomeScreenAction
}
