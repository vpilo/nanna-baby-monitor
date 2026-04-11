package org.vpilo.babymonitor.settings.presentation.composables

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class MenuSettingItemViewModel(
    private val settingsRepository: SettingsRepository,
    private val setting: Setting<*>,
) : AppViewModel<MenuSettingItemAction, MenuSettingItemState, Unit>(
        initialState = MenuSettingItemState(value = setting.default),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        settingsRepository.flowOf(setting).subscribe { newValue ->
            MenuSettingItemState(value = newValue).update()
        }
    }

    override fun onAction(action: MenuSettingItemAction) {
        vmScope.launch {
            when (action) {
                is MenuSettingItemAction.SetValue -> {
                    @Suppress("UNCHECKED_CAST")
                    settingsRepository.save(setting as Setting<Any>, action.value)
                }
            }
        }
    }
}
