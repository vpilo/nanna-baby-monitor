package org.vpilo.babymonitor.app.menu

import androidx.compose.runtime.Stable
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.network.model.repository.IsConnectionAvailableRepository
import org.vpilo.babymonitor.network.model.repository.NetworkClientRepository

@Stable
class MenuScreenViewModel(
    private val appRoleRepository: AppRoleRepository,
    private val isConnectionAvailableRepository: IsConnectionAvailableRepository,
    private val networkClientRepository: NetworkClientRepository,
) : AppViewModel<MenuScreenAction, MenuScreenState, MenuScreenEffect>(
        initialState = MenuScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        appRoleRepository.appRole.subscribe {
            state.copy(currentRole = it).update()
        }
        isConnectionAvailableRepository.isConnectionAvailableFlow.subscribe {
            state.copy(isConnected = it).update()
        }
    }

    override fun onAction(action: MenuScreenAction) {
        when (action) {
            MenuScreenAction.Disconnect -> {
                vmScope.launch {
                    networkClientRepository.disconnect()
                    MenuScreenEffect.Disconnected.sendEffect()
                }
            }
        }
    }
}
