package org.vpilo.babymonitor.network.server.pairing

import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.model.pairing.PairedClient
import org.vpilo.babymonitor.network.model.pairing.PairingQrPayload
import org.vpilo.babymonitor.network.model.pairing.Pin
import org.vpilo.babymonitor.network.model.pairing.ServerPairingFailureReason
import org.vpilo.babymonitor.network.model.pairing.ServerPairingState
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.security.crypto.EcdhKeyPair
import org.vpilo.babymonitor.network.security.crypto.buildPairingTranscript
import org.vpilo.babymonitor.network.security.crypto.computeServerConfirmation
import org.vpilo.babymonitor.network.security.crypto.deriveSharedSecretS
import org.vpilo.babymonitor.network.security.crypto.verifyClientConfirmation
import org.vpilo.babymonitor.network.security.pairing.PairingResult
import org.vpilo.babymonitor.network.security.protocol.receiveBase64FrameOrNull
import org.vpilo.babymonitor.network.security.protocol.receivePairingHelloOrNull
import org.vpilo.babymonitor.network.security.protocol.sendBase64Frame
import org.vpilo.babymonitor.network.security.protocol.sendPairingResult
import org.vpilo.babymonitor.network.server.identity.ServerIdentity
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the server side of the pairing window: PIN/QR generation, the 3-minute expiry timer, the
 * 5-attempt lockout, and the ECDH+PIN exchange for a single `/pair` session.
 */
internal class PairingCoordinator(
    private val pairingStorageRepository: PairingStorageRepository,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())

    private val _state = MutableStateFlow<ServerPairingState>(ServerPairingState.Idle)
    val state: StateFlow<ServerPairingState> = _state.asStateFlow()

    @Volatile
    private var activeWindow: ActiveWindow? = null

    fun startPairingWindow(self: Device.LocalServer) {
        val pin = Pin.generate()
        val qrText =
            PairingQrPayload(
                deviceId = self.id,
                pin = pin,
            ).asPayloadString()
        activeWindow = ActiveWindow(pin = pin, remainingAttempts = MAX_PIN_ATTEMPTS)
        _state.value = ServerPairingState.Active(pin, qrText)

        scope.launch {
            delay(PAIRING_WINDOW_DURATION)
            if (activeWindow?.pin == pin) {
                activeWindow = null
                _state.value = ServerPairingState.Failed(ServerPairingFailureReason.WINDOW_EXPIRED)
            }
        }
    }

    fun cancelPairingWindow() {
        activeWindow = null
        _state.value = ServerPairingState.Idle
    }

    /** Runs the server side of the ECDH+PIN exchange over an already-open, TLS-terminated `/pair` session. */
    suspend fun handlePairingSession(
        session: WebSocketSession,
        serverIdentity: ServerIdentity,
    ) {
        val window = activeWindow
        if (window == null) {
            Logger.w(TAG) { "Rejecting pairing session: no active pairing window" }
            session.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "No active pairing window"))
            return
        }

        val hello =
            session.receivePairingHelloOrNull() ?: run {
                session.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed hello"))
                return
            }

        val serverKeyPair = EcdhKeyPair.create()
        session.sendBase64Frame(serverKeyPair.publicKeyEncoded)

        val transcript = buildPairingTranscript(hello.publicKey, serverKeyPair.publicKeyEncoded, serverIdentity.fingerprint)
        val mc =
            session.receiveBase64FrameOrNull() ?: run {
                session.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed confirmation"))
                return
            }

        if (!verifyClientConfirmation(window.pin, transcript, mc)) {
            registerWrongAttempt(window)
            session.sendPairingResult(PairingResult.Failure("wrong-pin"))
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Wrong PIN"))
            return
        }

        val sharedSecret = deriveSharedSecretS(serverKeyPair.deriveSharedSecret(hello.publicKey))
        val ms = computeServerConfirmation(window.pin, transcript)
        session.sendPairingResult(PairingResult.Success(ms))

        pairingStorageRepository.pairClient(
            PairedClient(
                deviceId = hello.clientId.toString(),
                name = hello.clientName,
                sharedSecretBase64 = Base64.encode(sharedSecret),
                pairedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        activeWindow = null
        _state.value = ServerPairingState.Succeeded(hello.clientName)
        Logger.i(TAG) { "Paired with client ${hello.clientId}" }
    }

    private fun registerWrongAttempt(window: ActiveWindow) {
        window.remainingAttempts--
        Logger.w(TAG) { "Wrong PIN attempt, ${window.remainingAttempts} remaining" }
        if (window.remainingAttempts <= 0) {
            activeWindow = null
            _state.value = ServerPairingState.Failed(ServerPairingFailureReason.WRONG_PIN_LOCKOUT)
        }
    }

    private class ActiveWindow(
        val pin: Pin,
        var remainingAttempts: Int,
    )

    private companion object {
        private val TAG = PairingCoordinator::class
        private val PAIRING_WINDOW_DURATION = 2.minutes
        private const val MAX_PIN_ATTEMPTS = 5
    }
}
