package org.vpilo.babymonitor.network.server.session

import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks which [WebSocketSession]s are currently authenticated as which [DeviceId], so a revoked
 * client's already-active sessions can be cut off immediately instead of only at their next reconnect.
 */
internal class ActiveSessionRegistry {
    private val sessionsByClient = ConcurrentHashMap<DeviceId, CopyOnWriteArrayList<WebSocketSession>>()

    fun register(
        clientId: DeviceId,
        session: WebSocketSession,
    ) {
        // compute (unlike computeIfAbsent(...).add(...)) performs the create-or-lookup AND the add atomically in
        // one remapping call, so a concurrent closeSessionsForClient() can't remove(clientId) in the gap between
        // computeIfAbsent returning and add() running and orphan this session onto a list no longer in the map.
        sessionsByClient.compute(clientId) { _, existing ->
            (existing ?: CopyOnWriteArrayList()).apply { add(session) }
        }
    }

    fun unregister(
        clientId: DeviceId,
        session: WebSocketSession,
    ) {
        // computeIfPresent runs its remapping atomically per key, so a concurrent register() can't race with the
        // empty-check here and get its just-added session dropped along with the entry.
        sessionsByClient.computeIfPresent(clientId) { _, sessions ->
            sessions.remove(session)
            sessions.ifEmpty { null }
        }
    }

    suspend fun closeSessionsForClient(clientId: DeviceId) {
        val sessions = sessionsByClient.remove(clientId) ?: return
        sessions.forEach { session ->
            // One dead/already-closing session throwing must not stop the rest of this client's sessions from
            // being cut off too.
            runCatching {
                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Revoked"))
            }.onFailure { Logger.w(TAG, it) { "Failed to close a session for revoked client $clientId" } }
        }
        Logger.i(TAG) { "Closed ${sessions.size} active session(s) for revoked client $clientId" }
    }

    private companion object {
        private val TAG = ActiveSessionRegistry::class
    }
}
