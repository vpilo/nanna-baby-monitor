package org.vpilo.babymonitor.settings.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.settings.model.Setting

interface SettingsRepository {

    fun <T : Any> getAsFlow(
        key: Setting<T>,
    ): Flow<T>

    suspend fun <T : Any> get(
        key: Setting<T>,
    ): T

    suspend fun <T : Any> set(
        key: Setting<T>,
        value: T,
    )

    suspend fun <T : Any> clear(key: Setting<T>)
}
