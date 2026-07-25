package org.vpilo.babymonitor.network.internal.repository

import io.ktor.websocket.WebSocketSession
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.repository.ActiveSessionsRepository

/**
 * Internal interface for [ActiveSessionsRepository] that exposes methods to register and unregister sessions.
 * This is used by the network server and client to track active sessions for each client.
 */
interface InternalActiveSessionsRepository : ActiveSessionsRepository {
    fun register(
        clientId: DeviceId,
        session: WebSocketSession,
    )

    fun unregister(
        clientId: DeviceId,
        session: WebSocketSession,
    )
}
