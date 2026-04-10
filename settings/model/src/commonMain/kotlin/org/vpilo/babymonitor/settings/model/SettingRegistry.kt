package org.vpilo.babymonitor.settings.model

import org.vpilo.babymonitor.model.settings.SettingId

object SettingRegistry {
    private val registry: MutableSet<Setting<*>> = mutableSetOf()

    val settings: Set<Setting<*>>
        get() = registry.toSet()

    fun register(vararg settings: Setting<*>) {
        val ids = settings.map { it.id }
        check(ids.none { newId -> registry.any { it.id == newId } }) {
            "Duplicate setting id: ${ids.filter { newId -> registry.any { it.id == newId } }}"
        }
        registry.addAll(settings)
    }

    fun getById(id: SettingId): Setting<*> = registry.first { it.id == id }
}
