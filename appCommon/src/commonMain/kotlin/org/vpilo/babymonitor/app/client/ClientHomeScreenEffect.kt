package org.vpilo.babymonitor.app.client

sealed interface ClientHomeScreenEffect {
    object DisconnectedFromServer : ClientHomeScreenEffect {
        override fun toString() = "DisconnectedFromServer"
    }
}
