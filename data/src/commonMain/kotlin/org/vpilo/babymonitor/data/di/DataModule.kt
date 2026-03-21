package org.vpilo.babymonitor.data.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.data.DefaultAppRoleRepository
import org.vpilo.babymonitor.model.repository.AppRoleRepository

val dataKoinModule: Module = module {
    single<AppRoleRepository> { DefaultAppRoleRepository() }
}
