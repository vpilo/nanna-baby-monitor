package org.vpilo.babymonitor.settings.presentation.composables

import org.vpilo.babymonitor.model.settings.SettingId

data class MenuSettingItemState(
    val id: SettingId,
    val value: Any,
) {
    override fun toString(): String = "MenuSettingItemState($id, <${value.hashCode()}>)"
}
