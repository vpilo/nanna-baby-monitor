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
                attemptPairing(action.pin)
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
        val qrPayload =
            PairingQrPayload.fromPayloadStringOrNull(qrContent)
                ?: run {
                    state.copy(pairingState = ClientPairingState.Failure(ClientPairingFailureCause.INVALID_QR)).update()
                    return
                }

        if (qrPayload.deviceId != server) {
            state.copy(pairingState = ClientPairingState.Failure(ClientPairingFailureCause.WRONG_DEVICE)).update()
            return
        }

        attemptPairing(qrPayload.pin)
    }

    private fun attemptPairing(pin: Pin) {
        val server = state.server ?: return

        vmScope.launch {
            state.copy(pairingState = ClientPairingState.InProgress).update()
            val clientDevice = localClientDeviceRepository.localDevice.first()
            val outcome = clientPairingRepository.pairWith(server, clientDevice, pin)
            state.copy(pairingState = outcome).update()
            if (outcome is ClientPairingState.Success) {
                pairingStorageRepository.pair(outcome.paired)
                ClientPairingScreenEffect.Paired.sendEffect()
            }
        }
    }
}
