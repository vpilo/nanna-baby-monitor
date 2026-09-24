package org.vpilo.babymonitor.network.security.protocol

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import org.vpilo.babymonitor.network.security.pairing.PairingHello
import org.vpilo.babymonitor.network.security.pairing.PairingResult
import kotlin.io.encoding.Base64

suspend fun WebSocketSession.sendPairingHello(hello: PairingHello) = send(Frame.Text(hello.toWireString()))

suspend fun WebSocketSession.receivePairingHelloOrNull(): PairingHello? {
    val frame = incoming.receive()
    if (frame !is Frame.Text) {
        Logger.w(TAG) { "Pairing hello is not a text frame" }
        return null
    }
    return parsePairingHelloOrNull(frame.readText())
}

/** `clientId|clientName|clientCertFingerprint|pubKeyB64`. */
internal fun PairingHello.toWireString(): String =
    listOf(clientId.toString(), clientName, certFingerprint, Base64.encode(publicKey)).joinToString(HELLO_SEPARATOR)

/** Parses from both ends: the name is the only free-form field and may itself contain the separator. */
internal fun parsePairingHelloOrNull(text: String): PairingHello? {
    val parts = text.split(HELLO_SEPARATOR)
    if (parts.size < HELLO_FIELD_COUNT) {
        Logger.w(TAG) { "Pairing hello has ${parts.size} fields, expected $HELLO_FIELD_COUNT" }
        return null
    }
    val clientId =
        parts.first().toDeviceIdOrNull() ?: run {
            Logger.w(TAG) { "Pairing hello carries an unparseable device id: '${parts.first()}'" }
            return null
        }
    val certFingerprint = parts[parts.size - 2]
    if (!FINGERPRINT_REGEX.matches(certFingerprint)) {
        Logger.w(TAG) { "Pairing hello carries a malformed certificate fingerprint" }
        return null
    }
    val publicKey =
        runCatching { Base64.decode(parts.last()) }.getOrNull() ?: run {
            Logger.w(TAG) { "Pairing hello carries an unparseable public key" }
            return null
        }
    val clientName = parts.subList(1, parts.size - 2).joinToString(HELLO_SEPARATOR)
    return PairingHello(clientId, clientName, certFingerprint, publicKey)
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

private const val TAG = "PairingProtocol"
private const val HELLO_SEPARATOR = "|"
private const val HELLO_FIELD_COUNT = 4
private val FINGERPRINT_REGEX = Regex("[0-9a-f]{64}")
