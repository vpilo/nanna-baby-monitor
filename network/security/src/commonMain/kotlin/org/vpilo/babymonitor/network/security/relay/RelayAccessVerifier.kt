package org.vpilo.babymonitor.network.security.relay

import io.ktor.websocket.WebSocketSession
import kotlinx.coroutines.withTimeoutOrNull
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.security.crypto.computeRelayAccessProof
import org.vpilo.babymonitor.network.security.crypto.generateRelayNonce
import org.vpilo.babymonitor.network.security.crypto.verifyConnectorAccessProof
import org.vpilo.babymonitor.network.security.protocol.receiveRelayAccessRequestOrNull
import org.vpilo.babymonitor.network.security.protocol.sendRelayAccessChallenge
import org.vpilo.babymonitor.network.security.protocol.sendRelayAccessProof

/**
 * Relay-side counterpart of [relayWss]: challenges the connector and returns whether it proved it holds the
 * relay passphrase.
 *
 * Says nothing on rejection - no error frame, no close reason of its own - so a caller guessing passphrases
 * learns only that the connection ended, and never obtains a relay proof to attack offline. The relay proves
 * itself only *after* the connector has, for the same reason.
 *
 * [relayCertFingerprint] is this relay's own TLS leaf fingerprint, which the connector independently hashes off
 * the certificate it was served; they differ precisely when something is intercepting the connection.
 */
suspend fun WebSocketSession.verifyRelayAccess(
    accessKey: ByteArray,
    role: AppRole,
    endpoint: String,
    relayCertFingerprint: String,
): Boolean {
    val relayNonce = generateRelayNonce()
    sendRelayAccessChallenge(relayNonce)

    val request =
        withTimeoutOrNull(Constants.RELAY_HANDSHAKE_TIMEOUT) {
            receiveRelayAccessRequestOrNull()
        } ?: return false

    val verified =
        verifyConnectorAccessProof(
            accessKey = accessKey,
            role = role,
            endpoint = endpoint,
            relayCertFingerprint = relayCertFingerprint,
            connectorNonce = request.nonce,
            relayNonce = relayNonce,
            proof = request.proof,
        )
    if (!verified) return false

    sendRelayAccessProof(
        computeRelayAccessProof(
            accessKey = accessKey,
            role = role,
            endpoint = endpoint,
            relayCertFingerprint = relayCertFingerprint,
            connectorNonce = request.nonce,
            relayNonce = relayNonce,
        ),
    )
    return true
}
