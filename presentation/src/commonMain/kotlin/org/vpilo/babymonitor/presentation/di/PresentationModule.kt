package org.vpilo.babymonitor.presentation.di

import org.vpilo.babymonitor.presentation.test.TestRouteViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::TestRouteViewModel)
}
