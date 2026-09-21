package org.vpilo.babymonitor.settings.presentation.composables

sealed interface MenuSettingItemAction {
    data class SetValue(
        val value: Any,
    ) : MenuSettingItemAction {
        override fun toString() = "MenuSettingItemAction.SetValue(<${value.hashCode()}>)"
    }
}
