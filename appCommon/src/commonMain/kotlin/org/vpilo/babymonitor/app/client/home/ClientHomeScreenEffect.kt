package org.vpilo.babymonitor.app.client.home

sealed interface ClientHomeScreenEffect {
    object DisconnectedFromServer : ClientHomeScreenEffect {
        override fun toString() = "DisconnectedFromServer"
    }
}
