package org.vpilo.babymonitor.network.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object Constants {
    const val SERVICE_PORT = 47813

    const val RELAY_PORT = 47814

    const val DISCOVERY_SERVICE_TYPE = "babymonitor"

    const val SERVICES_LISTEN_ADDRESS: String = "0.0.0.0"
    val DISCOVERY_TRIM_PERIOD: Duration = 30.seconds

    const val TLS_SERVER_NAME = "BabyMonitorDevice"

    val RECONNECTION_TIMEOUT: Duration = 3.seconds
    val WEBSOCKET_PING_PERIOD: Duration = 30.seconds
    val WEBSOCKET_TIMEOUT: Duration = 10.seconds
    val WEBSOCKET_CONNECTION_ATTEMPT_DELAY: Duration = 100.milliseconds

    /** How long the relay waits for a connector to answer its access challenge. */
    val RELAY_HANDSHAKE_TIMEOUT: Duration = 10.seconds

    /** How long a client waits at the relay for the camera it asked for to dial back in. */
    val RELAY_RENDEZVOUS_TIMEOUT: Duration = 15.seconds

    val SERVER_STOP_GRACE_PERIOD: Duration = 5.seconds
}
