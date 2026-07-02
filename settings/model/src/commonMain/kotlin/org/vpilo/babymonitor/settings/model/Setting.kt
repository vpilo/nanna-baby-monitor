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
    val validateChange: suspend (T) -> T?

    companion object {
        fun makeString(
            id: SettingId,
            name: StringResource? = null,
            description: StringResource? = null,
            platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
            default: String,
            validateChange: suspend (String) -> String? = { it },
        ) = PrimitiveSetting(
            id = id,
            name = name,
            description = description,
            platform = platform,
            default = default,
            type = String::class,
            validateChange = validateChange,
        )

        fun makeBoolean(
            id: SettingId,
            name: StringResource? = null,
            description: StringResource? = null,
            platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
            default: Boolean,
            validateChange: suspend (Boolean) -> Boolean? = { it },
        ) = PrimitiveSetting(
            id = id,
            name = name,
            description = description,
            platform = platform,
            default = default,
            type = Boolean::class,
            validateChange = validateChange,
        )

        fun makeInt(
            id: SettingId,
            name: StringResource? = null,
            description: StringResource? = null,
            platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
            default: Int,
            limits: IntRange? = null,
            validateChange: suspend (Int) -> Int? = { it },
        ) = PrimitiveSetting(
            id = id,
            name = name,
            description = description,
            platform = platform,
            default = default,
            type = Int::class,
            validateChange = validateChange,
            limits = limits,
        )

        inline fun <reified E : Enum<E>> makeEnum(
            id: SettingId,
            name: StringResource? = null,
            description: StringResource? = null,
            platform: PlatformAvailability = PlatformAvailability.AllPlatforms,
            values: Map<E, StringResource>,
            default: E,
            noinline validateChange: suspend (E) -> E? = { it },
        ) = EnumSetting(
            id = id,
            name = name,
            description = description,
            platform = platform,
            default = default,
            type = E::class,
            values = values,
            validateChange = validateChange,
        ).also {
            require(values.isEmpty() || values.size == E::class.java.enumConstants?.size) {
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
    override val validateChange: suspend (T) -> T? = { it },
    val limits: IntRange? = null,
) : Setting<T> {
    init {
        require(limits == null || (type == Int::class && (default as Int) in limits)) {
            "Default value $default for setting $id is outside of limits $limits"
        }
        require(limits == null || limits.first <= limits.last) {
            "Limits $limits for setting $id are invalid"
        }
    }

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
    override val validateChange: suspend (T) -> T? = { it },
    val values: Map<T, StringResource>,
) : Setting<T> {
    override fun toString() = "Setting('$id': $type)"

    companion object
}
