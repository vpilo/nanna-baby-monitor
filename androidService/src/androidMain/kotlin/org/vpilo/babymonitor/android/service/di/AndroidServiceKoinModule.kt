package org.vpilo.babymonitor.android.service.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.android.service.AndroidServiceHost
import org.vpilo.babymonitor.android.service.LifecycleService

val androidServiceKoinModule = module {
    singleOf(::AndroidServiceHost).bind(LifecycleService::class)
}
