package org.vpilo.babymonitor.network.security.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import org.vpilo.babymonitor.network.security.relay.RelayAccessRequest
import kotlin.io.encoding.Base64

suspend fun WebSocketSession.sendRelayAccessChallenge(nonce: ByteArray) = sendBase64Frame(nonce)

suspend fun WebSocketSession.receiveRelayAccessChallengeOrNull(): ByteArray? = receiveBase64FrameOrNull()

suspend fun WebSocketSession.sendRelayAccessRequest(
    nonce: ByteArray,
    proof: ByteArray,
) = send(Frame.Text(listOf(Base64.encode(nonce), Base64.encode(proof)).joinToString("|")))

suspend fun WebSocketSession.receiveRelayAccessRequestOrNull(): RelayAccessRequest? {
    val frame = incoming.receive()
    if (frame !is Frame.Text) return null
    val parts = frame.readText().split("|")
    if (parts.size != 2) return null
    val nonce = runCatching { Base64.decode(parts[0]) }.getOrNull() ?: return null
    val proof = runCatching { Base64.decode(parts[1]) }.getOrNull() ?: return null
    return RelayAccessRequest(nonce, proof)
}

suspend fun WebSocketSession.sendRelayAccessProof(proof: ByteArray) = sendBase64Frame(proof)

suspend fun WebSocketSession.receiveRelayAccessProofOrNull(): ByteArray? = receiveBase64FrameOrNull()
