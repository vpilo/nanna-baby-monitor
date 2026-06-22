package org.vpilo.babymonitor.network.common.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.vpilo.babymonitor.network.common.discovery.DiscoveryManager

val networkCommonKoinModule: Module =
    module {
        singleOf(::DiscoveryManager)
    }
