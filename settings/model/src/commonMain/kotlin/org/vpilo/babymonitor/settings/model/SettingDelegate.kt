package org.vpilo.babymonitor.settings.model

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

/**
 * Setting creation property delegate.
 * Lazily creates and registers a setting.
 */
class SettingDelegate<T : Any, S : Setting<T>>(
    getSetting: () -> S,
) : ReadOnlyProperty<Setting.Companion, S> {
    private val loadedSetting = getSetting()

    override fun getValue(thisRef: Setting.Companion, property: KProperty<*>): S {
        SettingRegistry.register(loadedSetting)
        return loadedSetting
    }
}

/**
 * Use to register a new setting.
 */
fun <T : Any> makeSetting(getSetting: () -> Setting<T>): ReadOnlyProperty<Setting.Companion, Setting<T>> =
    SettingDelegate(getSetting)
