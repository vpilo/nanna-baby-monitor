package org.vpilo.babymonitor.network.security.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import org.vpilo.babymonitor.network.security.pairing.PairingHello
import org.vpilo.babymonitor.network.security.pairing.PairingResult
import kotlin.io.encoding.Base64

suspend fun WebSocketSession.sendPairingHello(
    clientId: DeviceId,
    clientName: String,
    publicKey: ByteArray,
) = send(Frame.Text(listOf(clientId, clientName, Base64.encode(publicKey)).joinToString("|")))

suspend fun WebSocketSession.receivePairingHelloOrNull(): PairingHello? {
    val frame = incoming.receive()
    if (frame !is Frame.Text) return null
    val parts = frame.readText().split("|", limit = 3)
    if (parts.size != 3) return null
    val clientId = parts[0].toDeviceIdOrNull() ?: return null
    val publicKey = runCatching { Base64.decode(parts[2]) }.getOrNull() ?: return null
    return PairingHello(clientId, parts[1], publicKey)
}

suspend fun WebSocketSession.sendBase64Frame(bytes: ByteArray) = send(Frame.Text(Base64.encode(bytes)))

suspend fun WebSocketSession.receiveBase64FrameOrNull(): ByteArray? {
    val frame = incoming.receive()
    if (frame !is Frame.Text) return null
    return runCatching { Base64.decode(frame.readText()) }.getOrNull()
}

suspend fun WebSocketSession.sendPairingResult(result: PairingResult) {
    when (result) {
        is PairingResult.Success -> send(Frame.Text("OK|${Base64.encode(result.serverConfirmation)}"))
        is PairingResult.Failure -> send(Frame.Text("ERROR|${result.reason}"))
    }
}

suspend fun WebSocketSession.receivePairingResultOrNull(): PairingResult? {
    val frame = incoming.receive()
    if (frame !is Frame.Text) return null
    val parts = frame.readText().split("|", limit = 2)
    if (parts.size != 2) return null
    val (key, payload) = parts
    return when (key) {
        "OK" -> runCatching { PairingResult.Success(Base64.decode(payload)) }.getOrNull()
        "ERROR" -> PairingResult.Failure(payload)
        else -> null
    }
}
