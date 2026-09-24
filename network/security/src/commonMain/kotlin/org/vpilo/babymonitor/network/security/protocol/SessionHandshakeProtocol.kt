package org.vpilo.babymonitor.network.security.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import kotlin.io.encoding.Base64

data class SessionHandshakeRequest(
    val clientId: DeviceId,
    val salt: ByteArray,
    val proof: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionHandshakeRequest

        if (clientId != other.clientId) return false
        if (!salt.contentEquals(other.salt)) return false
        if (!proof.contentEquals(other.proof)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = clientId.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + proof.contentHashCode()
        return result
    }
}

suspend fun WebSocketSession.sendSessionHandshakeRequest(
    clientId: DeviceId,
    salt: ByteArray,
    proof: ByteArray,
) = send(Frame.Text(listOf(clientId, Base64.encode(salt), Base64.encode(proof)).joinToString("|")))

suspend fun WebSocketSession.receiveSessionHandshakeRequestOrNull(): SessionHandshakeRequest? {
    val frame = incoming.receive()
    if (frame !is Frame.Text) return null
    val parts = frame.readText().split("|")
    if (parts.size != 3) return null
    val clientId = parts[0].toDeviceIdOrNull() ?: return null
    val salt = runCatching { Base64.decode(parts[1]) }.getOrNull() ?: return null
    val proof = runCatching { Base64.decode(parts[2]) }.getOrNull() ?: return null
    return SessionHandshakeRequest(clientId, salt, proof)
}

data class SessionHandshakeResponse(
    val salt: ByteArray,
    val proof: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SessionHandshakeResponse

        if (!salt.contentEquals(other.salt)) return false
        if (!proof.contentEquals(other.proof)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = salt.contentHashCode()
        result = 31 * result + proof.contentHashCode()
        return result
    }
}

suspend fun WebSocketSession.sendSessionHandshakeResponse(
    salt: ByteArray,
    proof: ByteArray,
) = send(Frame.Text(listOf(Base64.encode(salt), Base64.encode(proof)).joinToString("|")))

suspend fun WebSocketSession.receiveSessionHandshakeResponseOrNull(): SessionHandshakeResponse? {
    val frame = incoming.receive()
    if (frame !is Frame.Text) return null
    val parts = frame.readText().split("|")
    if (parts.size != 2) return null
    val salt = runCatching { Base64.decode(parts[0]) }.getOrNull() ?: return null
    val proof = runCatching { Base64.decode(parts[1]) }.getOrNull() ?: return null
    return SessionHandshakeResponse(salt, proof)
}
