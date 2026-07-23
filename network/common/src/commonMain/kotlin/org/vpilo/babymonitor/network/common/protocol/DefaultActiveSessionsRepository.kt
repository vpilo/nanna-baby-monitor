package org.vpilo.babymonitor.network.common.protocol

import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.common.repository.InternalActiveSessionsRepository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Tracks which [WebSocketSession]s are currently authenticated as which [DeviceId], so an unpaired device's active sessions can be
 * cut off immediately.
 */
internal class DefaultActiveSessionsRepository : InternalActiveSessionsRepository {
    private val sessionsByClient = ConcurrentHashMap<DeviceId, CopyOnWriteArrayList<WebSocketSession>>()

    override fun register(
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

    override fun unregister(
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

    override suspend fun closeSessions(
        clientId: DeviceId,
        wasUnpaired: Boolean,
    ) {
        val sessions = sessionsByClient.remove(clientId) ?: return
        sessions.forEach { session ->
            // One dead/already-closing session throwing must not stop the rest of this client's sessions from
            // being cut off too.
            val reason =
                if (wasUnpaired) {
                    CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unpaired")
                } else {
                    CloseReason(CloseReason.Codes.GOING_AWAY, "Server closing")
                }
            runCatching {
                session.close(reason)
            }.onFailure {
                Logger.w(TAG, it) { "Failed to close a session for client $clientId (was unpaired=$wasUnpaired)" }
            }
        }
        Logger.i(TAG) { "Closed ${sessions.size} active session(s) for client $clientId" }
    }

    private companion object {
        private val TAG = DefaultActiveSessionsRepository::class
    }
}
