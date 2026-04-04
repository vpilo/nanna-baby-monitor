package org.vpilo.babymonitor.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import java.io.IOException

class DefaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun <T : Any> flowOf(setting: Setting<T>): Flow<T> =
        dataStore.getSafeFlow()
            .map { preferences -> setting.getValueOrDefault(preferences) }
            .distinctUntilChanged()

    override suspend fun <T : Any> load(setting: Setting<T>): T =
        setting.getValueOrDefault(dataStore.getSafeFlow().first())

    private fun <T : Any> Setting<T>.getValueOrDefault(preferences: Preferences): T {
        return when (type) {
            Boolean::class,
            Int::class,
            Long::class,
            Float::class,
            Double::class,
            ByteArray::class,
            String::class -> (preferences[toDataStoreKey()] ?: default) as T

            Enum::class -> {
                (preferences[stringPreferencesKey(id)]
                    ?.let { enumValue -> type.java.enumConstants.firstOrNull { (it as Enum<*>).name == enumValue } }
                    ?: default) as T
            }

            else -> error("Unsupported type $type for setting $id")
        }
    }

    override suspend fun <T : Any> save(setting: Setting<T>, value: T) {
        dataStore.edit { settings ->
            when (setting.type) {
                Boolean::class,
                Int::class,
                Long::class,
                Float::class,
                Double::class,
                ByteArray::class,
                String::class -> settings[setting.toDataStoreKey()] = value

                Enum::class -> {
                    check(value is Enum<*>) { "Value $value is not an enum for setting ${setting.id}" }
                    settings[stringPreferencesKey(setting.id)] = value.name
                }

                else -> error("Unsupported type ${setting.type} for setting ${setting.id}")
            }
        }
    }

    override suspend fun <T : Any> clear(setting: Setting<T>) {
        val key = setting.toDataStoreKey()
        dataStore.edit { settings ->
            settings.remove(key)
        }
    }

    private fun DataStore<Preferences>.getSafeFlow(): Flow<Preferences> =
        data.catch {
            // throws an IOException when an error is encountered when reading data
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }

    private fun <T : Any> Setting<T>.toDataStoreKey(): Preferences.Key<T> {
        @Suppress("UNCHECKED_CAST")
        return when (type) {
            Boolean::class -> booleanPreferencesKey(id)
            Int::class -> intPreferencesKey(id)
            Float::class -> floatPreferencesKey(id)
            String::class -> stringPreferencesKey(id)
            else -> {
                error("Unsupported type $type for setting $id")
            }
        } as Preferences.Key<T>
    }

    private fun <T : Enum<*>> Setting<T>.toEnumDataStoreKey(): Preferences.Key<String> {
        check(type is Enum<*>) { "Unsupported type $type for enum setting $id" }
        return stringPreferencesKey(id)
    }
}
