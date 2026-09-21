package org.vpilo.babymonitor.settings.presentation.composables

data class MenuSettingItemState(
    val value: Any,
) {
    override fun toString(): String = "MenuSettingItemState(<${value.hashCode()}>)"
}
