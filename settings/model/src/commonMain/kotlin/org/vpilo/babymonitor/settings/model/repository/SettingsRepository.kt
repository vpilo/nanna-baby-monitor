package org.vpilo.babymonitor.settings.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.settings.model.Setting

interface SettingsRepository {

    fun <T : Any> flowOf(setting: Setting<T>): Flow<T>

    suspend fun <T : Any> load(setting: Setting<T>): T

    suspend fun <T : Any> save(setting: Setting<T>, value: T)

    suspend fun <T : Any> clear(setting: Setting<T>)
}
