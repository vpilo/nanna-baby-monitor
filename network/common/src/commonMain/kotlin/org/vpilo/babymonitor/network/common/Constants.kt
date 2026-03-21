package org.vpilo.babymonitor.network.common

import java.net.InetAddress

object Constants {
    const val WEBSOCKET_PORT = 47812

    const val DISCOVERY_PORT = 47813

    val SERVICES_LISTEN_ADDRESS: InetAddress = InetAddress.getLocalHost()
}
