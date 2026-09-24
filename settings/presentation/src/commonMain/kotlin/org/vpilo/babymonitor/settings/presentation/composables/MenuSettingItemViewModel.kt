package org.vpilo.babymonitor.settings.presentation.composables

import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class MenuSettingItemViewModel(
    private val settingsRepository: SettingsRepository,
    private val setting: Setting<*>,
) : AppViewModel<MenuSettingItemAction, MenuSettingItemState, Unit>(
        initialState = MenuSettingItemState(id = setting.id, value = setting.default),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        settingsRepository.flowOf(setting).subscribe { newValue ->
            MenuSettingItemState(id = setting.id, value = newValue).update()
        }
    }

    override fun onAction(action: MenuSettingItemAction) {
        when (action) {
            is MenuSettingItemAction.SetValue -> {
                state.copy(value = action.value).update()
                @Suppress("UNCHECKED_CAST")
                settingsRepository.saveDelayed(setting as Setting<Any>, action.value)
            }
        }
    }
}
