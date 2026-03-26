package org.vpilo.babymonitor.app.clientconnectionchooser

import java.net.InetAddress

sealed interface ClientConnectionChooserEffect {
    data class Connected(val address: InetAddress) : ClientConnectionChooserEffect
}
