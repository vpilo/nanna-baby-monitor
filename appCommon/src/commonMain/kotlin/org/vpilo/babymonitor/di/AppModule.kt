package org.vpilo.babymonitor.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.vpilo.babymonitor.app.client.ClientHomeViewModel
import kotlin.coroutines.CoroutineContext

expect val appPlatformModule: Module

val appSharedKoinModules = listOf(
    module {
        single<CoroutineContext> { Dispatchers.Default }

        viewModelOf(::ClientHomeViewModel)
    },
    appPlatformModule,
)
