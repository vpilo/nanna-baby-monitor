package org.vpilo.babymonitor.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.coroutines.CoroutineContext

expect val appPlatformModule: Module

val appSharedKoinModules = listOf(
    module {
        single<CoroutineContext> { Dispatchers.Default }
    },
    appPlatformModule,
)
