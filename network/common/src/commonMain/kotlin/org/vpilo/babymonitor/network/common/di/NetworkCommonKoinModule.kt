package org.vpilo.babymonitor.network.common.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.network.common.discovery.DefaultLocalDiscoveryRepository
import org.vpilo.babymonitor.network.common.protocol.DefaultActiveSessionsRepository
import org.vpilo.babymonitor.network.common.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.ActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository

val networkCommonKoinModule: Module =
    module {
        singleOf(::DefaultLocalDiscoveryRepository)
            .bind<LocalDiscoveryRepository>()
        singleOf(::DefaultActiveSessionsRepository)
            .bind<InternalActiveSessionsRepository>()
            .bind<ActiveSessionsRepository>()
    }
