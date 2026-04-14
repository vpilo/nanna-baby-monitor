package org.vpilo.babymonitor.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class DefaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
    coroutineContext: CoroutineContext,
) : SettingsRepository {
    private var saveDelayedJob: Job? = null
    private val saveDelayedScope = CoroutineScope(coroutineContext)
    private val saveDelayedValues = mutableMapOf<Setting<*>, Any>()

    override fun <T : Any> flowOf(setting: Setting<T>): Flow<T> =
        dataStore
            .getSafeFlow()
            .map { preferences -> setting.getValueOrDefault(preferences) }
            .distinctUntilChanged()

    override suspend fun <T : Any> load(setting: Setting<T>): T = setting.getValueOrDefault(dataStore.getSafeFlow().first())

    private fun <T : Any> Setting<T>.getValueOrDefault(preferences: Preferences): T {
        return when (type) {
            Boolean::class,
            Int::class,
            Long::class,
            Float::class,
            Double::class,
            ByteArray::class,
            String::class,
                -> {
                    preferences[toDataStoreKey()] ?: default
                }

            else -> {
                if (type.java.isEnum) {
                    val savedString = preferences[stringPreferencesKey(id.value)] ?: return default
                    type.java.enumConstants?.firstOrNull { (it as Enum<*>).name == savedString }
                        ?: run {
                            Logger.w(TAG) {
                                "Saved value '$savedString' for setting '$id' does not match any enum constant!"
                            }
                            default
                        }
                } else {
                    error("Unsupported type $type for setting $id")
                }
            }
        }
    }

    override suspend fun <T : Any> save(
        setting: Setting<T>,
        value: T,
    ) {
        val validatedValue =
            setting.validateChange(value)
                ?: run {
                    Logger.w(TAG) { "Validation failed for value '$value' of '${setting.id}': keeping previous value." }
                    return
                }
        dataStore.edit { settings ->
            when (setting.type) {
                Boolean::class,
                Int::class,
                Long::class,
                Float::class,
                Double::class,
                ByteArray::class,
                String::class,
                    -> {
                        settings[setting.toDataStoreKey()] = validatedValue
                    }

                else -> {
                    if (setting.type.java.isEnum) {
                        check(validatedValue is Enum<*>) { "Value $validatedValue is not an enum for setting ${setting.id}" }
                        settings[stringPreferencesKey(setting.id.value)] = validatedValue.name
                    } else {
                        error("Unsupported type ${setting.type} for setting ${setting.id}")
                    }
                }
            }
        }
    }

    override fun <T : Any> saveDelayed(
        setting: Setting<T>,
        value: T,
    ) {
        saveDelayedValues[setting] = value
        saveDelayedJob?.cancel()
        saveDelayedJob =
            saveDelayedScope.launch {
                delay(SAVE_DELAY)
                val entries =
                    synchronized(saveDelayedValues) {
                        val entries = saveDelayedValues.entries.toList()
                        saveDelayedValues.clear()
                        entries
                    }
                entries.forEach { (setting, value) ->
                    @Suppress("UNCHECKED_CAST")
                    save(setting as Setting<Any>, value)
                }
            }
    }

    override suspend fun <T : Any> clear(setting: Setting<T>) {
        val key =
            if (setting.type.java.isEnum) {
                stringPreferencesKey(setting.id.value)
            } else {
                setting.toDataStoreKey()
            }
        dataStore.edit { settings ->
            settings.remove(key)
        }
    }

    private fun DataStore<Preferences>.getSafeFlow(): Flow<Preferences> =
        data.catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }

    private fun <T : Any> Setting<T>.toDataStoreKey(): Preferences.Key<T> {
        @Suppress("UNCHECKED_CAST")
        return when (type) {
            Boolean::class -> {
                booleanPreferencesKey(id.value)
            }

            Int::class -> {
                intPreferencesKey(id.value)
            }

            Float::class -> {
                floatPreferencesKey(id.value)
            }

            String::class -> {
                stringPreferencesKey(id.value)
            }

            else -> {
                error("Unsupported type $type for setting $id")
            }
        } as Preferences.Key<T>
    }

    private companion object {
        private val TAG = DefaultSettingsRepository::class

        private val SAVE_DELAY = 2.seconds
    }
}
