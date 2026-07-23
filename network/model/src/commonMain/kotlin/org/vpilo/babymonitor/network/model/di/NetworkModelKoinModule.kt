package org.vpilo.babymonitor.network.model.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.vpilo.babymonitor.network.model.usecase.GetPairedDevicesFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.UnpairDeviceUseCase

val networkModelKoinModule: Module =
    module {
        factoryOf(::GetPairedDevicesFlowUseCase)
        factoryOf(::UnpairDeviceUseCase)
    }
