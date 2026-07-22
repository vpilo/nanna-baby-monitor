package org.vpilo.babymonitor.app.server.paireddevices

import androidx.compose.runtime.Stable
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.toDeviceId
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.network.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.network.model.usecase.GetPairedDevicesFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.UnpairDeviceUseCase

@Stable
class PairedDevicesScreenViewModel(
    private val networkServerRepository: NetworkServerRepository,
    private val getPairedDevicesFlowUseCase: GetPairedDevicesFlowUseCase,
    private val unpairDeviceUseCase: UnpairDeviceUseCase,
) : AppViewModel<PairedDevicesScreenAction, PairedDevicesScreenState, Unit>(
        initialState = PairedDevicesScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        getPairedDevicesFlowUseCase.invoke().subscribe { clients -> state.copy(devices = clients).update() }
    }

    override fun onAction(action: PairedDevicesScreenAction) {
        when (action) {
            is PairedDevicesScreenAction.Revoke -> {
                vmScope.launch {
                    val clientId = action.clientId.toDeviceId()
                    unpairDeviceUseCase(clientId)
                    networkServerRepository.closeSessionsForClient(clientId)
                }
            }
        }
    }
}
