package org.vpilo.babymonitor.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.model.AppRole
import org.vpilo.babymonitor.model.repository.AppRoleRepository

internal class DefaultAppRoleRepository : AppRoleRepository {
    private val _appRole: MutableStateFlow<AppRole> =
        MutableStateFlow(AppRole.UNDECIDED)
    override val appRole: Flow<AppRole> = _appRole.asStateFlow()

    override suspend fun chooseRole(appRole: AppRole) {
        _appRole.value = appRole
    }
}
