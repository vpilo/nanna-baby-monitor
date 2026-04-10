package org.vpilo.babymonitor.settings.model

import org.jetbrains.compose.resources.StringResource
import org.vpilo.babymonitor.model.settings.SettingId
import kotlin.reflect.KClass

sealed interface Setting<T : Any> {
    val id: SettingId
    val name: StringResource?
    val description: StringResource?
    val platform: PlatformAvailability
    val type: KClass<T>
    val default: T

    companion object {
        inline fun <reified T : Any> makePrimitive(
            id: SettingId,
            name: StringResource? = null,
            description: StringResource? = null,
            platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
            default: T,
        ) = PrimitiveSetting(
            id = id,
            name = name,
            description = description,
            platform = platform,
            default = default,
            type = T::class,
        )

        inline fun <reified E : Enum<E>> makeEnum(
            id: SettingId,
            name: StringResource? = null,
            description: StringResource? = null,
            platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
            values: Map<E, StringResource>,
            default: E,
        ) = EnumSetting(
            id = id,
            name = name,
            description = description,
            platform = platform,
            default = default,
            type = E::class,
            values = values,
        ).also {
            require(values.isEmpty() || values.size == E::class.java.enumConstants.size) {
                "Enum setting $id must have strings for all enum values."
            }
        }
    }
}

class PrimitiveSetting<T : Any>(
    override val id: SettingId,
    override val name: StringResource? = null,
    override val description: StringResource? = null,
    override val platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
    override val type: KClass<T>,
    override val default: T,
) : Setting<T> {
    override fun toString() = "Setting('$id': $type)"

    companion object
}

class EnumSetting<T : Enum<*>>(
    override val id: SettingId,
    override val name: StringResource? = null,
    override val description: StringResource? = null,
    override val platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
    override val type: KClass<T>,
    override val default: T,
    val values: Map<T, StringResource>,
) : Setting<T> {
    override fun toString() = "Setting('$id': $type)"

    companion object
}
