package org.vpilo.babymonitor.errorreport.presentation.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.vpilo.babymonitor.errorreport.presentation.ErrorReportViewModel

val errorReportPresentationKoinModule: Module =
    module {
        viewModelOf(::ErrorReportViewModel)
    }
