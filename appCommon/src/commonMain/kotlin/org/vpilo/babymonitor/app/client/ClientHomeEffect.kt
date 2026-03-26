package org.vpilo.babymonitor.app.client

sealed interface ClientHomeEffect {
        object DisconnectFromServer : ClientHomeEffect {
            override fun toString() = "DisconnectFromServer"
        }
}
