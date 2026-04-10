package org.vpilo.babymonitor.app.menu

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

class MenuScreenViewModel :
    AppViewModel<MenuScreenScreenAction, MenuScreenState, MenuScreenEffect>(
        initialState = MenuScreenState(),
    ) {
    override fun onAction(action: MenuScreenScreenAction) {
        vmScope.launch {
            when (action) {
                is MenuScreenScreenAction.TBD -> {
                }
            }
        }
    }
}
