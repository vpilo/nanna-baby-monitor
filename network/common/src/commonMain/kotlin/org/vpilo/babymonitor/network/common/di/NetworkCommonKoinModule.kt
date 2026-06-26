package org.vpilo.babymonitor.network.common.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.network.common.discovery.DefaultLocalDiscoveryRepository

val networkCommonKoinModule: Module =
    module {
        singleOf(::DefaultLocalDiscoveryRepository)
            .bind<LocalDiscoveryRepository>()
    }
