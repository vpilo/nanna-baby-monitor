package org.vpilo.babymonitor.network.security.relay

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPathPart
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import io.ktor.websocket.pingInterval
import io.ktor.websocket.timeout
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.withTimeoutOrNull
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.protocol.RELAY_PROTOCOL_VERSION
import org.vpilo.babymonitor.network.internal.protocol.asVersionMismatchExceptionOrNull
import org.vpilo.babymonitor.network.internal.protocol.sendProtocolVersion
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.RelayConfiguration
import org.vpilo.babymonitor.network.security.crypto.PinnedTrustManager
import org.vpilo.babymonitor.network.security.crypto.computeConnectorAccessProof
import org.vpilo.babymonitor.network.security.crypto.generateRelayNonce
import org.vpilo.babymonitor.network.security.crypto.sha256Fingerprint
import org.vpilo.babymonitor.network.security.crypto.verifyRelayAccessProof
import org.vpilo.babymonitor.network.security.protocol.receiveRelayAccessChallengeOrNull
import org.vpilo.babymonitor.network.security.protocol.receiveRelayAccessProofOrNull
import org.vpilo.babymonitor.network.security.protocol.sendRelayAccessRequest
import io.ktor.client.plugins.websocket.pingInterval as clientPingInterval

/**
 * Opens an authenticated relay connection and runs [block] on it.
 *
 * Every relay connection goes through here.
 * [block] is only run once the connection has been proven to have a compatible version and to hold the relay passphrase, and once the
 * relay has proven the same back.
 *
 * [endpoint] is one of the `Endpoints.Relay` constants and is bound into both proofs; [serverId], when given, is
 * appended to the request path but deliberately left out of the proofs - the relay routes on it, and binding it
 * would force both sides to agree on its encoding for no security gain. [port] only ever moves for tests.
 *
 * The TLS certificate is not validated against any PKI. It is instead folded into the handshake transcript, so a
 * relay presenting a different certificate than the one both sides hash - an interceptor - fails to authenticate.
 */
suspend fun relayWss(
    configuration: RelayConfiguration,
    role: AppRole,
    endpoint: String,
    serverId: DeviceId? = null,
    port: Int = Constants.RELAY_PORT,
    block: suspend DefaultClientWebSocketSession.() -> Unit,
) {
    require(configuration.isConfigured) { "Cannot connect to an unconfigured relay" }

    // Derived before the socket exists: the first derivation is slow enough on a phone to outlast the relay's
    // challenge timeout if it happened mid-handshake.
    val accessKey = RelayAccessKey.forPassphrase(configuration.passphrase)

    val trustManager = PinnedTrustManager(expectedFingerprint = null)
    val client =
        HttpClient(CIO) {
            install(WebSockets) { clientPingInterval = Constants.WEBSOCKET_PING_PERIOD }
            engine {
                https {
                    this.trustManager = trustManager
                    // The relay may be reached by bare IP, which SNI can't carry.
                    serverName = Constants.TLS_SERVER_NAME
                }
            }
        }

    client.use {
        it.wss(
            method = HttpMethod.Get,
            host = configuration.host,
            port = port,
            path = if (serverId == null) endpoint else "$endpoint/${serverId.toString().encodeURLPathPart()}",
        ) {
            pingInterval = Constants.WEBSOCKET_PING_PERIOD
            timeout = Constants.WEBSOCKET_TIMEOUT

            sendProtocolVersion(RELAY_PROTOCOL_VERSION)
            throwIfRelayVersionMismatches()
            if (!authenticateWithRelay(accessKey, role, endpoint, trustManager)) return@wss
            block()
        }
    }
}

/**
 * A rejecting relay says nothing and closes, so every receive here can end in a closed channel rather than a
 * value. That is the expected shape of "wrong passphrase" - not an error worth propagating to the retry loops,
 * which would report it as a generic connection failure and bury the one thing the user needs to be told.
 */
private suspend fun DefaultClientWebSocketSession.authenticateWithRelay(
    accessKey: ByteArray,
    role: AppRole,
    endpoint: String,
    trustManager: PinnedTrustManager,
): Boolean {
    val fingerprint =
        trustManager.capturedCertificate?.sha256Fingerprint() ?: run {
            Logger.e(TAG) { "Relay presented no certificate for $endpoint" }
            close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "No relay certificate"))
            return false
        }

    val relayNonce =
        runCatching { receiveRelayAccessChallengeOrNull() }.getOrNull() ?: run {
            Logger.w(TAG) { "No usable relay challenge for $endpoint" }
            close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed relay challenge"))
            return false
        }

    val connectorNonce = generateRelayNonce()
    sendRelayAccessRequest(
        nonce = connectorNonce,
        proof = computeConnectorAccessProof(accessKey, role, endpoint, fingerprint, connectorNonce, relayNonce),
    )

    val relayProof =
        runCatching { receiveRelayAccessProofOrNull() }.getOrNull() ?: run {
            Logger.w(TAG) { "Relay refused access to $endpoint - check the relay passphrase" }
            return false
        }

    if (!verifyRelayAccessProof(accessKey, role, endpoint, fingerprint, connectorNonce, relayNonce, relayProof)) {
        Logger.e(TAG) { "Relay failed to prove itself for $endpoint - the connection is being intercepted" }
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Relay authentication failed"))
        return false
    }
    return true
}

// The relay refuses a mismatching version before sending its challenge, and says which version it speaks.
@OptIn(DelicateCoroutinesApi::class)
private suspend fun DefaultClientWebSocketSession.throwIfRelayVersionMismatches() {
    if (!incoming.isClosedForReceive) return
    withTimeoutOrNull(Constants.WEBSOCKET_TIMEOUT) { closeReason.await() }
        ?.asVersionMismatchExceptionOrNull(RELAY_PROTOCOL_VERSION)
        ?.let { throw it }
}

private const val TAG = "RelayConnection"
