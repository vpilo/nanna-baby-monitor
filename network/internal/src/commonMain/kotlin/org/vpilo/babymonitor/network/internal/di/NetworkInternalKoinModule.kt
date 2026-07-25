package org.vpilo.babymonitor.network.internal.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.network.internal.discovery.DefaultLocalDiscoveryRepository
import org.vpilo.babymonitor.network.internal.protocol.DefaultActiveSessionsRepository
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.ActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository

val networkInternalKoinModule: Module =
    module {
        singleOf(::DefaultLocalDiscoveryRepository)
            .bind<LocalDiscoveryRepository>()
        singleOf(::DefaultActiveSessionsRepository)
            .bind<InternalActiveSessionsRepository>()
            .bind<ActiveSessionsRepository>()
    }
