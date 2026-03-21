package org.vpilo.babymonitor.app.clientconnectionchooser

import java.net.InetAddress

sealed interface ClientConnectionChooserAction {
    data class ConnectToServer(val address: InetAddress) : ClientConnectionChooserAction
}
