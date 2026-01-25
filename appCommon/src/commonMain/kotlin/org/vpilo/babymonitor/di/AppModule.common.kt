package org.vpilo.babymonitor.di

import org.vpilo.babymonitor.data.HttpClientFactory
import org.koin.core.module.Module
import org.koin.dsl.module

expect val appPlatformModule: Module

val sharedModule =
    module {
        single { HttpClientFactory.create(get()) }
    }
