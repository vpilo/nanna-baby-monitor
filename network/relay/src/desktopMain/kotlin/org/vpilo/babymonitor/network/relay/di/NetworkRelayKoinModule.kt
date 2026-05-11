package org.vpilo.babymonitor.network.relay.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.relay.DefaultNetworkRelayRepository
import kotlin.coroutines.CoroutineContext

val networkRelayKoinModule: Module =
    module {
        single<CoroutineContext> { Dispatchers.Default }

        singleOf(::DiscoveryManager)
        singleOf(::DefaultNetworkRelayRepository)
    }
