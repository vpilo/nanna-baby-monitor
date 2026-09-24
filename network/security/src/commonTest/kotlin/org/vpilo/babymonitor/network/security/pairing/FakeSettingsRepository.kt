package org.vpilo.babymonitor.network.security.pairing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

internal class FakeSettingsRepository(
    initial: Map<SettingId, Any> = emptyMap(),
) : SettingsRepository {
    val values = MutableStateFlow(initial)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> flowOf(setting: Setting<T>): Flow<T> = values.map { (it[setting.id] as T?) ?: setting.default }

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> load(setting: Setting<T>): T = (values.value[setting.id] as T?) ?: setting.default

    override suspend fun <T : Any> save(
        setting: Setting<T>,
        value: T,
    ) = values.update { it + (setting.id to value) }

    override fun <T : Any> saveDelayed(
        setting: Setting<T>,
        value: T,
    ) = values.update { it + (setting.id to value) }

    override suspend fun <T : Any> clear(setting: Setting<T>) = values.update { it - setting.id }
}
