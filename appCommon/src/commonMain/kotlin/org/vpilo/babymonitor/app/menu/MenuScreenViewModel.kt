package org.vpilo.babymonitor.app.menu

import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

class MenuScreenViewModel(
    private val appRoleRepository: AppRoleRepository,
) : AppViewModel<Unit, MenuScreenState, Unit>(
        initialState = MenuScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        appRoleRepository.appRole.subscribe {
            state.copy(currentRole = it).update()
        }
    }
}
