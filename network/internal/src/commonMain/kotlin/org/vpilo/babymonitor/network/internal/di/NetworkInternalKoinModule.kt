package org.vpilo.babymonitor.network.internal.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.network.internal.discovery.DefaultDiscoveredDeviceCacheRepository
import org.vpilo.babymonitor.network.internal.discovery.DefaultLocalDiscoveryClientRepository
import org.vpilo.babymonitor.network.internal.discovery.DefaultLocalDiscoveryServerRepository
import org.vpilo.babymonitor.network.internal.discovery.LocalDiscoveryDataSource
import org.vpilo.babymonitor.network.internal.discovery.LocalDiscoveryServerDataSource
import org.vpilo.babymonitor.network.internal.protocol.DefaultActiveSessionsRepository
import org.vpilo.babymonitor.network.internal.protocol.DefaultRelayConfigurationRepository
import org.vpilo.babymonitor.network.internal.repository.InternalActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.ActiveSessionsRepository
import org.vpilo.babymonitor.network.model.repository.DiscoveredDeviceCacheRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryClientRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryServerRepository
import org.vpilo.babymonitor.network.model.repository.RelayConfigurationRepository
import org.vpilo.babymonitor.settings.model.getCacheStoreFile

val networkInternalKoinModule: Module =
    module {
        singleOf(::DefaultLocalDiscoveryClientRepository)
            .bind<LocalDiscoveryClientRepository>()
        singleOf(::DefaultLocalDiscoveryServerRepository)
            .bind<LocalDiscoveryServerRepository>()
        singleOf(::DefaultRelayConfigurationRepository)
            .bind<RelayConfigurationRepository>()
        singleOf(::DefaultActiveSessionsRepository)
            .bind<InternalActiveSessionsRepository>()
            .bind<ActiveSessionsRepository>()
        singleOf(::LocalDiscoveryDataSource)
        singleOf(::LocalDiscoveryServerDataSource)

        single<DiscoveredDeviceCacheRepository> {
            val dataStore =
                PreferenceDataStoreFactory.create {
                    getCacheStoreFile()
                }
            DefaultDiscoveredDeviceCacheRepository(dataStore = dataStore, coroutineContext = get())
        }
    }
