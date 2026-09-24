package org.vpilo.babymonitor.errorreport.data.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.vpilo.babymonitor.errorreport.data.DefaultErrorReportingRepository
import org.vpilo.babymonitor.errorreport.model.ErrorReportingRepository

val errorReportDataKoinModule: Module =
    module {
        singleOf(::DefaultErrorReportingRepository)
            .bind<ErrorReportingRepository>()
    }
