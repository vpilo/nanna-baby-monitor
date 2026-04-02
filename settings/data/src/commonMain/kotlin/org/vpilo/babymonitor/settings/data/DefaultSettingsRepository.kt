package org.vpilo.babymonitor.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

class DefaultSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override fun <T : Any> getAsFlow(key: Setting<T>): Flow<T> {
        TODO("Not yet implemented")
    }

    override suspend fun <T : Any> get(key: Setting<T>): T {
        TODO("Not yet implemented")
    }

    override suspend fun <T : Any> set(key: Setting<T>, value: T) {
        TODO("Not yet implemented")
    }

    override suspend fun <T : Any> clear(key: Setting<T>) {
        TODO("Not yet implemented")
    }
}
