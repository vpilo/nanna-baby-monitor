package org.vpilo.babymonitor.network.client.session

import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.common.crypto.SessionFrameCipher
import org.vpilo.babymonitor.network.common.crypto.asClientCipher
import org.vpilo.babymonitor.network.common.crypto.computeClientHandshakeProof
import org.vpilo.babymonitor.network.common.crypto.deriveSessionKeys
import org.vpilo.babymonitor.network.common.crypto.generateSessionSalt
import org.vpilo.babymonitor.network.common.crypto.verifyServerHandshakeProof
import org.vpilo.babymonitor.network.common.protocol.StreamType
import org.vpilo.babymonitor.network.common.protocol.receiveSessionHandshakeResponseOrNull
import org.vpilo.babymonitor.network.common.protocol.sendSessionHandshakeRequest
import org.vpilo.babymonitor.network.model.repository.PairingRepository
import kotlin.io.encoding.Base64

/** Runs the client side of the per-connection session handshake; returns `null` (and closes [this]) on any failure. */
suspend fun WebSocketSession.clientSessionHandshake(
    clientId: DeviceId,
    serverDeviceId: DeviceId,
    pairingRepository: PairingRepository,
    streamType: StreamType,
): SessionFrameCipher? {
    val paired = pairingRepository.findServer(serverDeviceId)
    if (paired == null) {
        Logger.w(TAG) { "Not paired with $serverDeviceId, refusing session" }
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No longer paired"))
        return null
    }

    val sharedSecret = Base64.decode(paired.sharedSecretBase64)
    val clientSalt = generateSessionSalt()
    sendSessionHandshakeRequest(clientId, clientSalt, computeClientHandshakeProof(sharedSecret, clientSalt))

    val response =
        receiveSessionHandshakeResponseOrNull() ?: run {
            close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed session handshake"))
            return null
        }
    if (!verifyServerHandshakeProof(sharedSecret, clientSalt, response.salt, response.proof)) {
        Logger.w(TAG) { "Invalid session proof from $serverDeviceId" }
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid session proof"))
        return null
    }

    val keys = deriveSessionKeys(sharedSecret, clientSalt, response.salt)
    val associatedData = serverDeviceId.toByteArray() + byteArrayOf(streamType.tag)
    return keys.asClientCipher(associatedData)
}

private const val TAG = "ClientSessionHandshake"
