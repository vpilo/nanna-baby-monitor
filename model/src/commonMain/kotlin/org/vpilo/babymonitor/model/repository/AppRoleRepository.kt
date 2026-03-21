package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.AppRole

interface AppRoleRepository {

    val appRole: Flow<AppRole>

    suspend fun chooseRole(appRole: AppRole)
}
