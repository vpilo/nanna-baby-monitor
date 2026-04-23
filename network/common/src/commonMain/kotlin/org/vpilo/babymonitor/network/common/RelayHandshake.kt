package org.vpilo.babymonitor.network.common

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import kotlinx.coroutines.withTimeoutOrNull
import org.vpilo.babymonitor.common.Logger
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private const val RELAY_PASSWORD = "babymonitor-relay-secret"

fun deriveSharedRelaySecret(): ByteArray {
    val hash = MessageDigest.getInstance("SHA-256").digest(RELAY_PASSWORD.toByteArray(Charsets.UTF_8))
    return ByteArray(RelayHandshake.HANDSHAKE_SIZE) { hash[it % hash.size] }
}

object RelayHandshake {
    const val HANDSHAKE_SIZE = 256

    suspend fun await(
        session: WebSocketSession,
        secret: ByteArray,
    ): Boolean {
        val data =
            withTimeoutOrNull(HANDSHAKE_TIMEOUT) {
                session.incoming.receive().readBytes()
            }
        return (data != null && data.size == HANDSHAKE_SIZE && data.contentEquals(secret))
            .also {
                if (!it) Logger.w(TAG) { "Handshake failed!" }
            }
    }

    suspend fun send(
        session: WebSocketSession,
        secret: ByteArray,
    ) {
        session.send(Frame.Binary(fin = true, data = secret))
    }

    private val HANDSHAKE_TIMEOUT: Duration = 2.seconds

    private val TAG = RelayHandshake::class
}
