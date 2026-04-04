package org.vpilo.babymonitor.settings.model

import org.jetbrains.compose.resources.StringResource
import kotlin.reflect.KClass

data class Setting<T : Any>(
    val id: String,
    val name: StringResource? = null,
    val description: StringResource? = null,
    val category: SettingCategory = SettingCategory.Internal,
    val platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
    val type: KClass<T>,
    val default: T,
) {
    override fun toString() = "Setting('$id': $type)"

    companion object
}
