package org.vpilo.babymonitor.app.client.pairing

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.LocalClientDeviceRepository
import org.vpilo.babymonitor.model.repository.toDeviceIdOrNull
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.network.model.pairing.ClientPairingFailureCause
import org.vpilo.babymonitor.network.model.pairing.ClientPairingRepository
import org.vpilo.babymonitor.network.model.pairing.ClientPairingState
import org.vpilo.babymonitor.network.model.pairing.PairingQrPayload
import org.vpilo.babymonitor.network.model.pairing.PairingQrPayload.Companion.fromPayloadString
import org.vpilo.babymonitor.network.model.pairing.Pin
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository

@Stable
class ClientPairingScreenViewModel(
    private val deviceId: String,
    private val localDiscoveryRepository: LocalDiscoveryRepository,
    private val pairingStorageRepository: PairingStorageRepository,
    private val clientPairingRepository: ClientPairingRepository,
    private val localClientDeviceRepository: LocalClientDeviceRepository,
) : AppViewModel<ClientPairingScreenAction, ClientPairingScreenState, ClientPairingScreenEffect>(
    TAG = "ClientPairingScreenViewModel",
    initialState = ClientPairingScreenState(),
) {
    override fun SubscriptionScope.onSubscribed() {
        localDiscoveryRepository.discoveredDevicesFlow.subscribe { devices ->
            val deviceId = deviceId.toDeviceIdOrNull()
            val server = devices.filterIsInstance<Device.Server>().firstOrNull { it.id == deviceId }
            if (deviceId == null || server == null || server !is Device.LocalServer) {
                ClientPairingScreenEffect.ServerUnavailable.sendEffect()
                return@subscribe
            }
            state.copy(server = server).update()
        }
    }

    override fun onAction(action: ClientPairingScreenAction) {
        when (action) {
            is ClientPairingScreenAction.SubmitPin -> {
                // Pairing with just the PIN is a fallback: it is vulnerable to MITM attacks.
                // The server fingerprint is not known, so the connection will be trusted on first use.
                attemptPairing(action.pin, serverFingerprint = "")
            }

            is ClientPairingScreenAction.SubmitQr -> {
                val server = state.server
                server ?: run {
                    ClientPairingScreenEffect.ServerUnavailable.sendEffect()
                    return
                }

                validateQr(server.id, action.qr)
            }
        }
    }

    private fun validateQr(
        server: DeviceId,
        qrContent: String,
    ) {
        val qrPayload = qrContent.fromPayloadString(expectedDeviceId = server)
        if (qrPayload is PairingQrPayload.Valid) {
            attemptPairing(qrPayload.pin, qrPayload.serverFingerprint)
            return
        }

        val failureReason =
            when (qrPayload as PairingQrPayload.Invalid) {
                PairingQrPayload.Invalid.WRONG_QR -> ClientPairingFailureCause.INVALID_QR
                PairingQrPayload.Invalid.VERSION_MISMATCH_REMOTE_OUTDATED -> ClientPairingFailureCause.CAMERA_OUTDATED
                PairingQrPayload.Invalid.VERSION_MISMATCH_LOCAL_OUTDATED -> ClientPairingFailureCause.MONITOR_OUTDATED
                PairingQrPayload.Invalid.INVALID_SERVER_FINGERPRINT -> ClientPairingFailureCause.MITM_SUSPECTED
                PairingQrPayload.Invalid.INVALID_DEVICE_ID -> ClientPairingFailureCause.WRONG_DEVICE
                PairingQrPayload.Invalid.WRONG_PIN -> ClientPairingFailureCause.WRONG_PIN
            }

        state.copy(pairingState = ClientPairingState.Failure(failureReason)).update()
    }

    private fun attemptPairing(pin: Pin, serverFingerprint: String) {
        val server = state.server ?: return

        vmScope.launch {
            state.copy(pairingState = ClientPairingState.InProgress).update()
            val clientDevice = localClientDeviceRepository.localDevice.first()
            val outcome = clientPairingRepository.pairWith(server, clientDevice, pin, serverFingerprint)
            state.copy(pairingState = outcome).update()
            if (outcome is ClientPairingState.Success) {
                pairingStorageRepository.pair(outcome.paired)
                ClientPairingScreenEffect.Paired.sendEffect()
            }
        }
    }
}
