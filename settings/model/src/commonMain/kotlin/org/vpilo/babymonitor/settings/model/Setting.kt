package org.vpilo.babymonitor.settings.model

import kotlin.reflect.KClass

data class Setting<T : Any>(
    val id: String,
    val name: String,
    val description: String?,
    val category: SettingCategory,
    val platform: PlatformAvailability,
    val type: KClass<T>,
    val default: T,
)
