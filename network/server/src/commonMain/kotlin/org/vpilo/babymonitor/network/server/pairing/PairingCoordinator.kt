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
import org.vpilo.babymonitor.model.repository.PairingFailureReason
import org.vpilo.babymonitor.model.repository.PairingWindowState
import org.vpilo.babymonitor.network.common.crypto.PAIRING_PROTOCOL_VERSION
import org.vpilo.babymonitor.network.common.crypto.PairingQrPayload
import org.vpilo.babymonitor.network.common.crypto.buildPairingTranscript
import org.vpilo.babymonitor.network.common.crypto.computeServerConfirmation
import org.vpilo.babymonitor.network.common.crypto.deriveSharedSecretS
import org.vpilo.babymonitor.network.common.crypto.encodeToQrText
import org.vpilo.babymonitor.network.common.crypto.generateEcdhKeyPair
import org.vpilo.babymonitor.network.common.crypto.generatePairingPin
import org.vpilo.babymonitor.network.common.crypto.verifyClientConfirmation
import org.vpilo.babymonitor.network.common.protocol.PairingResult
import org.vpilo.babymonitor.network.common.protocol.receiveBase64FrameOrNull
import org.vpilo.babymonitor.network.common.protocol.receivePairingHelloOrNull
import org.vpilo.babymonitor.network.common.protocol.sendBase64Frame
import org.vpilo.babymonitor.network.common.protocol.sendPairingResult
import org.vpilo.babymonitor.network.server.identity.ServerIdentity
import org.vpilo.babymonitor.network.server.identity.fingerprint
import org.vpilo.babymonitor.settings.model.repository.PairedClient
import org.vpilo.babymonitor.settings.model.repository.PairingRepository
import kotlin.coroutines.CoroutineContext
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes

/**
 * Runs the server side of the pairing window: PIN/QR generation, the 3-minute expiry timer, the
 * 5-attempt lockout, and the ECDH+PIN exchange for a single `/pair` session.
 */
class PairingCoordinator(
    private val pairingRepository: PairingRepository,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())

    private val _state = MutableStateFlow<PairingWindowState>(PairingWindowState.Idle)
    val state: StateFlow<PairingWindowState> = _state.asStateFlow()

    @Volatile private var activeWindow: ActiveWindow? = null

    fun startPairingWindow(self: Device.LocalServer) {
        val pin = generatePairingPin()
        val hostHint =
            self.addresses
                .firstOrNull()
                ?.hostAddress
                .orEmpty()
        val qrText = PairingQrPayload(PAIRING_PROTOCOL_VERSION, self.id, pin, hostHint).encodeToQrText()
        activeWindow = ActiveWindow(pin = pin, remainingAttempts = MAX_PIN_ATTEMPTS)
        _state.value = PairingWindowState.Active(pin, qrText)

        scope.launch {
            delay(PAIRING_WINDOW_DURATION)
            if (activeWindow?.pin == pin) {
                activeWindow = null
                _state.value = PairingWindowState.Failed(PairingFailureReason.WINDOW_EXPIRED)
            }
        }
    }

    fun cancelPairingWindow() {
        activeWindow = null
        _state.value = PairingWindowState.Idle
    }

    /** Runs the server side of the ECDH+PIN exchange over an already-open, TLS-terminated `/pair` session. */
    suspend fun handlePairingSession(
        session: WebSocketSession,
        serverIdentity: ServerIdentity,
    ) {
        val window = activeWindow
        if (window == null) {
            session.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "No active pairing window"))
            return
        }

        val hello =
            session.receivePairingHelloOrNull() ?: run {
                session.close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Malformed hello"))
                return
            }

        val serverKeyPair = generateEcdhKeyPair()
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

        pairingRepository.pairClient(
            PairedClient(
                clientId = hello.clientId.toString(),
                name = hello.clientName,
                sharedSecretBase64 = Base64.encode(sharedSecret),
                pairedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
        activeWindow = null
        _state.value = PairingWindowState.Succeeded(hello.clientName)
        Logger.i(TAG) { "Paired with client ${hello.clientId} (${hello.clientName})" }
    }

    private fun registerWrongAttempt(window: ActiveWindow) {
        window.remainingAttempts--
        Logger.w(TAG) { "Wrong PIN attempt, ${window.remainingAttempts} remaining" }
        if (window.remainingAttempts <= 0) {
            activeWindow = null
            _state.value = PairingWindowState.Failed(PairingFailureReason.WRONG_PIN_LOCKOUT)
        }
    }

    private class ActiveWindow(
        val pin: String,
        var remainingAttempts: Int,
    )

    private companion object {
        private val TAG = PairingCoordinator::class
        private val PAIRING_WINDOW_DURATION = 3.minutes
        private const val MAX_PIN_ATTEMPTS = 5
    }
}
