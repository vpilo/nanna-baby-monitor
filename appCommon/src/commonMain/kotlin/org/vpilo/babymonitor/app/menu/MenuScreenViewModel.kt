package org.vpilo.babymonitor.app.menu

import androidx.compose.runtime.Stable
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.repository.IsConnectionAvailableRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

@Stable
class MenuScreenViewModel(
    private val appRoleRepository: AppRoleRepository,
    private val isConnectionAvailableRepository: IsConnectionAvailableRepository,
) : AppViewModel<Unit, MenuScreenState, Unit>(
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
}
