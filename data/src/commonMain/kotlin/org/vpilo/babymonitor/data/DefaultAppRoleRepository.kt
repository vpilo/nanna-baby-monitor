package org.vpilo.babymonitor.data

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.data.settings.AppRole
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

internal class DefaultAppRoleRepository(
    private val settingsRepository: SettingsRepository,
) : AppRoleRepository {
    override val appRole: Flow<AppRole> = settingsRepository.flowOf(Setting.AppRole)

    override suspend fun chooseRole(appRole: AppRole) {
        settingsRepository.save(Setting.AppRole, appRole)
    }
}
