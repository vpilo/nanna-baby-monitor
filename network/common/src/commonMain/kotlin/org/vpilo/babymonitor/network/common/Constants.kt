package org.vpilo.babymonitor.network.common

import java.net.InetAddress

object Constants {
    const val WEBSOCKET_PORT = 47812

    const val DISCOVERY_PORT = 47813

    const val DISCOVERY_SERVICE_TYPE = "_babymonitor._tcp."
    const val DISCOVERY_SERVICE_NAME = "BabyMonitor"
    const val DISCOVERY_SERVICE_DESCRIPTION = "Baby Monitor service"

    val SERVICES_LISTEN_ADDRESS: InetAddress = InetAddress.getLocalHost()
}
