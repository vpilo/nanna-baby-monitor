package org.vpilo.babymonitor.network.server.session

import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.security.crypto.computeServerHandshakeProof
import org.vpilo.babymonitor.network.security.crypto.deriveServerSessionCipher
import org.vpilo.babymonitor.network.security.crypto.generateSessionSalt
import org.vpilo.babymonitor.network.security.crypto.verifyClientHandshakeProof
import org.vpilo.babymonitor.network.security.protocol.StreamType
import org.vpilo.babymonitor.network.security.protocol.receiveSessionHandshakeRequestOrNull
import org.vpilo.babymonitor.network.security.protocol.sendSessionHandshakeResponse
import kotlin.io.encoding.Base64

/** Runs the server side of the per-connection session handshake; returns `null` (and closes [this]) on any failure. */
internal suspend fun WebSocketSession.serverSessionHandshake(
    serverDeviceId: DeviceId,
    pairingStorageRepository: PairingStorageRepository,
    streamType: StreamType,
): ServerSessionHandshakeResult? {
    val request =
        receiveSessionHandshakeRequestOrNull() ?: run {
            close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed session handshake"))
            return null
        }

    val paired = pairingStorageRepository.find(request.clientId)
    if (paired == null) {
        Logger.w(TAG) { "Unknown or revoked client ${request.clientId}, refusing session" }
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No longer paired"))
        return null
    }

    val sharedSecret = Base64.decode(paired.sharedSecretBase64)
    if (!verifyClientHandshakeProof(sharedSecret, request.salt, request.proof)) {
        Logger.w(TAG) { "Invalid session proof from ${request.clientId}" }
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid session proof"))
        return null
    }

    val serverSalt = generateSessionSalt()
    sendSessionHandshakeResponse(serverSalt, computeServerHandshakeProof(sharedSecret, request.salt, serverSalt))

    val associatedData = serverDeviceId.toByteArray() + byteArrayOf(streamType.tag)
    val cipher = deriveServerSessionCipher(sharedSecret, request.salt, serverSalt, associatedData)
    return ServerSessionHandshakeResult(cipher, request.clientId)
}

private const val TAG = "ServerSessionHandshake"
