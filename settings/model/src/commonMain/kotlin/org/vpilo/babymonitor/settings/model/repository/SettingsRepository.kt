package org.vpilo.babymonitor.settings.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.settings.model.Setting

interface SettingsRepository {
    fun <T : Any> flowOf(setting: Setting<T>): Flow<T>

    suspend fun <T : Any> load(setting: Setting<T>): T

    suspend fun <T : Any> save(
        setting: Setting<T>,
        value: T,
    )

    /**
     * Saves the [value] for [setting] after a predefined delay.
     * Repeated calls to [saveDelayed] for the same [setting] will reset the delay; only the last value will be saved.
     *
     * This is useful when a setting can be repeatedly updated in a short time, to prevent excessive update notifications by
     * [flowOf]. Another scenario is saving a setting immediately before closing a coroutine context (e.g. when clearing a view model):
     * the caller cannot otherwise guarantee that the coroutine will always be able to call [save].
     */
    fun <T : Any> saveDelayed(
        setting: Setting<T>,
        value: T,
    )

    suspend fun <T : Any> clear(setting: Setting<T>)
}
