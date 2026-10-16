package org.vpilo.babymonitor.network.security.relay

import io.ktor.websocket.WebSocketSession
import org.vpilo.babymonitor.network.internal.protocol.receiveProtocolVersion

// Helper for the relay; all protocol versioning calls are in :network:internal and thus inaccessible.
suspend fun WebSocketSession.receiveRelayProtocolVersion(): Boolean = receiveProtocolVersion(isRelayConnection = true)
