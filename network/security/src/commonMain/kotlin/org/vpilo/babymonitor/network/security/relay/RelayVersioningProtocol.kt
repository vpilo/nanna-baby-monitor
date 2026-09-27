package org.vpilo.babymonitor.network.security.relay

import io.ktor.websocket.WebSocketSession
import org.vpilo.babymonitor.network.internal.protocol.RELAY_PROTOCOL_VERSION
import org.vpilo.babymonitor.network.internal.protocol.receiveProtocolVersion
import org.vpilo.babymonitor.network.internal.protocol.sendProtocolVersion

// Helper for the relay; all protocol versioning calls are in :network:internal and thus inaccessible.
suspend fun WebSocketSession.receiveRelayProtocolVersion(): Boolean =
    receiveProtocolVersion(RELAY_PROTOCOL_VERSION)
