package org.vpilo.babymonitor.settings.model

object SettingRegistry {

    private val registry: MutableSet<Setting<*>> = mutableSetOf()

    val settings: Set<Setting<*>> = registry.toSet()

    fun register(vararg settings: Setting<*>) {
        val ids = settings.map { it.id }
        check(ids.none { newId -> registry.any { it.id == newId } }) {
            "Duplicate setting id: ${ids.filter { newId -> registry.any { it.id == newId } }}"
        }
        registry.addAll(settings)
    }
}
