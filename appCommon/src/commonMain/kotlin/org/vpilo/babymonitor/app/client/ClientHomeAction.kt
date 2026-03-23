package org.vpilo.babymonitor.app.client

sealed interface ClientHomeAction {
    data object ToggleAudio : ClientHomeAction
}

