package org.vpilo.babymonitor.app.client

sealed interface ClientHomeScreenAction {
    data object ToggleAudio : ClientHomeScreenAction
}
