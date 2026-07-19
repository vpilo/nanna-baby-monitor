package org.vpilo.babymonitor.network.client.pairing

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.PairingFailureCause
import org.vpilo.babymonitor.model.repository.PairingState
import org.vpilo.babymonitor.network.client.PinnedTrustManager
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.crypto.EcdhKeyPair
import org.vpilo.babymonitor.network.common.crypto.buildPairingTranscript
import org.vpilo.babymonitor.network.common.crypto.computeClientConfirmation
import org.vpilo.babymonitor.network.common.crypto.deriveSharedSecretS
import org.vpilo.babymonitor.network.common.crypto.generateEcdhKeyPair
import org.vpilo.babymonitor.network.common.crypto.sha256Fingerprint
import org.vpilo.babymonitor.network.common.crypto.verifyServerConfirmation
import org.vpilo.babymonitor.network.common.protocol.PairingResult
import org.vpilo.babymonitor.network.common.protocol.receiveBase64FrameOrNull
import org.vpilo.babymonitor.network.common.protocol.receivePairingResultOrNull
import org.vpilo.babymonitor.network.common.protocol.sendBase64Frame
import org.vpilo.babymonitor.network.common.protocol.sendPairingHello
import org.vpilo.babymonitor.settings.model.repository.PairedServer
import org.vpilo.babymonitor.settings.model.repository.PairingRepository
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64

/**
 * Runs the client side of the pairing window: connects to the server's `/pair` endpoint with a
 * trust-on-first-use TLS trust manager, runs the ECDH+PIN exchange (Task 8), and — on success —
 * persists the derived shared secret and pinned server certificate fingerprint via [pairingRepository].
 */
internal class ClientPairingConnector(
    private val pairingRepository: PairingRepository,
) {
    suspend fun pairWith(
        server: Device.Server,
        clientDevice: Device.Client,
        pin: String,
    ): PairingState {
        val host = server.addresses.firstOrNull() ?: return PairingState.Failure(PairingFailureCause.SERVER_NOT_ON_NETWORK)
        val trustManager = PinnedTrustManager(expectedFingerprint = null)
        val client =
            HttpClient(CIO) {
                install(WebSockets)
                engine { https { this.trustManager = trustManager } }
            }

        return try {
            var outcome: PairingState = PairingState.Failure(PairingFailureCause.CONNECTION_FAILED)
            client.wss(
                method = HttpMethod.Get,
                host = host.hostAddress,
                port = Constants.SERVICE_PORT,
                path = Endpoints.PAIR,
            ) {
                val clientKeyPair = generateEcdhKeyPair()
                sendPairingHello(clientDevice.id, clientDevice.name, clientKeyPair.publicKeyEncoded)

                val serverPublicKey = receiveBase64FrameOrNull()
                val certificate = trustManager.capturedCertificate
                if (serverPublicKey == null || certificate == null) {
                    outcome = PairingState.Failure(PairingFailureCause.CONNECTION_FAILED)
                    return@wss
                }

                val transcript = buildPairingTranscript(clientKeyPair.publicKeyEncoded, serverPublicKey, certificate.sha256Fingerprint())
                sendBase64Frame(computeClientConfirmation(pin, transcript))

                outcome =
                    when (val result = receivePairingResultOrNull()) {
                        is PairingResult.Success -> {
                            handleServerConfirmation(result, pin, transcript, clientKeyPair, serverPublicKey, server, certificate)
                        }

                        is PairingResult.Failure -> {
                            PairingState.Failure(PairingFailureCause.WRONG_PIN)
                        }

                        null -> {
                            PairingState.Failure(PairingFailureCause.CONNECTION_FAILED)
                        }
                    }
            }
            outcome
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            Logger.w(TAG) { "Pairing with $server failed: $ex" }
            PairingState.Failure(PairingFailureCause.CONNECTION_FAILED)
        } finally {
            client.close()
        }
    }

    /**
     * Verifies the server's confirmation `Ms` against the client's own transcript before trusting anything the
     * server said. A mismatch here — unlike a wrong PIN, which the server itself detects and reports via
     * [PairingResult.Failure] — means a party other than the expected server produced this response: the TLS
     * connection didn't terminate where the client thinks it did. Reported as [PairingFailureCause.MITM_SUSPECTED],
     * and the pairing is *not* persisted.
     */
    private suspend fun handleServerConfirmation(
        result: PairingResult.Success,
        pin: String,
        transcript: ByteArray,
        clientKeyPair: EcdhKeyPair,
        serverPublicKey: ByteArray,
        server: Device.Server,
        certificate: X509Certificate,
    ): PairingState {
        if (!verifyServerConfirmation(pin, transcript, result.serverConfirmation)) {
            return PairingState.Failure(PairingFailureCause.MITM_SUSPECTED)
        }
        val sharedSecret = deriveSharedSecretS(clientKeyPair.deriveSharedSecret(serverPublicKey))
        pairingRepository.pairServer(
            PairedServer(
                deviceId = server.id.toString(),
                name = server.name,
                certFingerprint = certificate.sha256Fingerprint(),
                sharedSecretBase64 = Base64.encode(sharedSecret),
            ),
        )
        Logger.i(TAG) { "Paired with server $server" }
        return PairingState.Success
    }

    private companion object {
        private val TAG = ClientPairingConnector::class
    }
}
