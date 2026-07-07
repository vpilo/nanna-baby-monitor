package org.vpilo.babymonitor.network.server.session

import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.common.crypto.asServerCipher
import org.vpilo.babymonitor.network.common.crypto.computeServerHandshakeProof
import org.vpilo.babymonitor.network.common.crypto.deriveSessionKeys
import org.vpilo.babymonitor.network.common.crypto.generateSessionSalt
import org.vpilo.babymonitor.network.common.crypto.verifyClientHandshakeProof
import org.vpilo.babymonitor.network.common.protocol.StreamType
import org.vpilo.babymonitor.network.common.protocol.receiveSessionHandshakeRequestOrNull
import org.vpilo.babymonitor.network.common.protocol.sendSessionHandshakeResponse
import org.vpilo.babymonitor.settings.model.repository.PairingRepository
import kotlin.io.encoding.Base64

/** Runs the server side of the per-connection session handshake; returns `null` (and closes [this]) on any failure. */
suspend fun WebSocketSession.serverSessionHandshake(
    serverDeviceId: DeviceId,
    pairingRepository: PairingRepository,
    streamType: StreamType,
): ServerSessionHandshakeResult? {
    val request =
        receiveSessionHandshakeRequestOrNull() ?: run {
            close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed session handshake"))
            return null
        }

    val paired = pairingRepository.findClient(request.clientId)
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

    val keys = deriveSessionKeys(sharedSecret, request.salt, serverSalt)
    val associatedData = serverDeviceId.toByteArray() + byteArrayOf(streamType.tag)
    return ServerSessionHandshakeResult(keys.asServerCipher(associatedData), request.clientId)
}

private const val TAG = "ServerSessionHandshake"
