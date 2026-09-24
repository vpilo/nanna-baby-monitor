package org.vpilo.babymonitor.network.client.pairing

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.CloseReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.Endpoints
import org.vpilo.babymonitor.network.model.pairing.ClientPairingFailureCause
import org.vpilo.babymonitor.network.model.pairing.ClientPairingRepository
import org.vpilo.babymonitor.network.model.pairing.ClientPairingState
import org.vpilo.babymonitor.network.model.pairing.PairedDevice
import org.vpilo.babymonitor.network.model.pairing.Pin
import org.vpilo.babymonitor.network.security.crypto.EcdhKeyPair
import org.vpilo.babymonitor.network.security.crypto.PinnedTrustManager
import org.vpilo.babymonitor.network.security.crypto.buildPairingTranscript
import org.vpilo.babymonitor.network.security.crypto.computeClientConfirmation
import org.vpilo.babymonitor.network.security.crypto.deriveSharedSecretS
import org.vpilo.babymonitor.network.security.crypto.sha256Fingerprint
import org.vpilo.babymonitor.network.security.crypto.verifyServerConfirmation
import org.vpilo.babymonitor.network.security.identity.DeviceIdentity
import org.vpilo.babymonitor.network.security.pairing.PairingHello
import org.vpilo.babymonitor.network.security.pairing.PairingResult
import org.vpilo.babymonitor.network.security.protocol.receiveBase64FrameOrNull
import org.vpilo.babymonitor.network.security.protocol.receivePairingResultOrNull
import org.vpilo.babymonitor.network.security.protocol.sendBase64Frame
import org.vpilo.babymonitor.network.security.protocol.sendPairingHello
import java.net.InetAddress
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64

/**
 * Runs the client side of the pairing window: connects to the server's `/pair` endpoint with a
 * trust-on-first-use TLS trust manager, runs the ECDH+PIN exchange, and - on success - returns the
 * derived shared secret and pinned server certificate fingerprint for storage.
 */
internal class DefaultClientPairingRepository(
    private val loadIdentity: () -> DeviceIdentity,
) : ClientPairingRepository {
    override suspend fun pairWith(
        server: Device.Server,
        clientDevice: Device.Client,
        pin: Pin,
    ): ClientPairingState {
        if (server is Device.RemoteServer || server.addresses.isEmpty()) {
            Logger.w(TAG) { "Server $server has no addresses" }
            return ClientPairingState.Failure(ClientPairingFailureCause.SERVER_NOT_ON_NETWORK)
        }

        val identity =
            runCatching {
                withContext(Dispatchers.IO) { loadIdentity() }
            }.getOrElse {
                if (it is CancellationException) throw it
                Logger.e(TAG, it) { "Unable to load or create this device's identity" }
                return ClientPairingState.Failure(ClientPairingFailureCause.STORAGE_ERROR)
            }

        val trustManager = PinnedTrustManager(expectedFingerprint = null)
        val httpClient =
            HttpClient(CIO) {
                install(WebSockets)
                engine {
                    https {
                        this.trustManager = trustManager
                        serverName = Constants.TLS_SERVER_NAME
                    }
                }
            }

        httpClient.use { http ->
            server.addresses.forEach { address ->
                val outcome = http.pairWithHost(address, trustManager, server, clientDevice, identity.fingerprint, pin)
                if (outcome != GENERIC_FAILURE) {
                    return outcome
                }
            }
        }
        return GENERIC_FAILURE
    }

    private suspend fun HttpClient.pairWithHost(
        host: InetAddress,
        trustManager: PinnedTrustManager,
        server: Device.Server,
        clientDevice: Device.Client,
        clientCertFingerprint: String,
        pin: Pin,
    ): ClientPairingState {
        Logger.w(TAG) { "Connecting to $server to pair" }
        return try {
            var outcome: ClientPairingState = GENERIC_FAILURE
            wss(
                method = HttpMethod.Get,
                host = host.hostAddress,
                port = Constants.SERVICE_PORT,
                path = Endpoints.PAIR,
            ) {
                val certificate =
                    trustManager.capturedCertificate
                        ?: run {
                            Logger.w(TAG) { "TLS session did not present a certificate to pin" }
                            outcome = ClientPairingState.Failure(ClientPairingFailureCause.PROTOCOL_ERROR)
                            return@wss
                        }
                outcome = runPairing(certificate, server, clientDevice, clientCertFingerprint, pin)
            }
            outcome
        } catch (
            @Suppress("TooGenericExceptionCaught") ex: Exception,
        ) {
            Logger.w(TAG) { "Pairing with $server failed: ${ex.prettify()}" }
            GENERIC_FAILURE
        }
    }

    private suspend fun DefaultClientWebSocketSession.runPairing(
        certificate: X509Certificate,
        server: Device.Server,
        clientDevice: Device.Client,
        clientCertFingerprint: String,
        pin: Pin,
    ): ClientPairingState {
        try {
            val clientKeyPair = EcdhKeyPair.create()
            sendPairingHello(PairingHello(clientDevice.id, clientDevice.name, clientCertFingerprint, clientKeyPair.publicKeyEncoded))

            val serverPublicKey =
                receiveBase64FrameOrNull() ?: run {
                    Logger.w(TAG) { "Server did not answer the pairing hello with its public key" }
                    return ClientPairingState.Failure(ClientPairingFailureCause.PROTOCOL_ERROR)
                }

            val transcript =
                buildPairingTranscript(
                    clientKeyPair.publicKeyEncoded,
                    serverPublicKey,
                    certificate.sha256Fingerprint(),
                    clientCertFingerprint,
                )
            sendBase64Frame(computeClientConfirmation(pin, transcript))

            return when (val result = receivePairingResultOrNull()) {
                is PairingResult.Success -> {
                    handleServerConfirmation(result, pin, transcript, clientKeyPair, serverPublicKey, server, certificate)
                }

                is PairingResult.Failure -> {
                    ClientPairingState.Failure(ClientPairingFailureCause.WRONG_PIN)
                }

                null -> {
                    Logger.w(TAG) { "Server did not answer the pairing confirmation" }
                    GENERIC_FAILURE
                }
            }
        } catch (ex: ClosedReceiveChannelException) {
            val reason = closeReason.await() ?: throw ex
            return ClientPairingState.Failure(
                when (reason.knownReason) {
                    CloseReason.Codes.CANNOT_ACCEPT -> ClientPairingFailureCause.NO_ACTIVE_PAIRING_WINDOW
                    CloseReason.Codes.VIOLATED_POLICY -> ClientPairingFailureCause.WRONG_PIN
                    else -> ClientPairingFailureCause.CONNECTION_FAILED
                },
            )
        }
    }

    /**
     * Verifies the server's confirmation `Ms` against the client's own transcript before trusting anything the
     * server said. A mismatch here - unlike a wrong PIN, which the server itself detects and reports via
     * [PairingResult.Failure] - means a party other than the expected server produced this response: the TLS
     * connection didn't terminate where the client thinks it did. Reported as [ClientPairingFailureCause.MITM_SUSPECTED],
     * and the pairing is *not* persisted.
     */
    private suspend fun handleServerConfirmation(
        result: PairingResult.Success,
        pin: Pin,
        transcript: ByteArray,
        clientKeyPair: EcdhKeyPair,
        serverPublicKey: ByteArray,
        server: Device.Server,
        certificate: X509Certificate,
    ): ClientPairingState {
        if (!verifyServerConfirmation(pin, transcript, result.serverConfirmation)) {
            return ClientPairingState.Failure(ClientPairingFailureCause.MITM_SUSPECTED)
        }
        val sharedSecret = deriveSharedSecretS(clientKeyPair.deriveSharedSecret(serverPublicKey))
        val pairedDevice =
            PairedDevice(
                deviceId = server.id.toString(),
                name = server.name,
                certFingerprint = certificate.sha256Fingerprint(),
                sharedSecretBase64 = Base64.encode(sharedSecret),
            )
        Logger.i(TAG) { "Paired with server $server" }
        return ClientPairingState.Success(pairedDevice)
    }

    private companion object {
        private val GENERIC_FAILURE = ClientPairingState.Failure(ClientPairingFailureCause.CONNECTION_FAILED)

        private val TAG = DefaultClientPairingRepository::class
    }
}
