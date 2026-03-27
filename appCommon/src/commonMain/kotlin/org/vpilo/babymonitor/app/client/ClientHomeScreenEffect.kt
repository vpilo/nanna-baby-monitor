package org.vpilo.babymonitor.app.client

sealed interface ClientHomeScreenEffect {
    object DisconnectFromServer : ClientHomeScreenEffect {
        override fun toString() = "DisconnectFromServer"
    }
}
