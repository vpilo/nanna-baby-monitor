package org.vpilo.babymonitor.network.common

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object Constants {
    const val SERVICE_PORT = 47813

    const val RELAY_PORT = 47814

    const val DISCOVERY_SERVICE_TYPE = "babymonitor"

    const val SERVICES_LISTEN_ADDRESS: String = "0.0.0.0"

    val RECONNECTION_TIMEOUT: Duration = 3.seconds
    val WEBSOCKET_PING_PERIOD: Duration = 30.seconds
    val WEBSOCKET_TIMEOUT: Duration = 10.seconds
    val WEBSOCKET_CONNECTION_ATTEMPT_DELAY: Duration = 100.milliseconds

    // TODO still useful?
    val RELAY_HANDSHAKE_TIMEOUT: Duration = 2.seconds

    // TODO still useful?
    val RELAY_RENDEZVOUS_TIMEOUT: Duration = 15.seconds

    val SERVER_STOP_GRACE_PERIOD: Duration = 5.seconds
}
