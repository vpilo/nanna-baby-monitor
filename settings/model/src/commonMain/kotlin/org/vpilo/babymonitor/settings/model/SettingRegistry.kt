package org.vpilo.babymonitor.settings.model

import org.vpilo.babymonitor.common.Logger

object SettingRegistry {

    private val registry: MutableSet<Setting<*>> = mutableSetOf()

    val settings: Set<Setting<*>> = registry.toSet()

    fun register(vararg settings: Setting<*>) {
        Logger.w("SETTINGREGISTRY") { "Registering ${settings.map { it.id }}"}
        registry.addAll(settings)
    }
}
