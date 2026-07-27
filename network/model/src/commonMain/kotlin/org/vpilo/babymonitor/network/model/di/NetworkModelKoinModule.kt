package org.vpilo.babymonitor.network.model.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.vpilo.babymonitor.network.model.usecase.GetConnectableServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetNewServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedDevicesForCurrentRoleFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedNonVisibleServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetRelayConfigurationFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetVisibleServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.UnpairDeviceUseCase

val networkModelKoinModule: Module =
    module {
        factoryOf(::GetConnectableServersFlowUseCase)
        factoryOf(::GetNewServersFlowUseCase)
        factoryOf(::GetPairedDevicesForCurrentRoleFlowUseCase)
        factoryOf(::GetPairedNonVisibleServersFlowUseCase)
        factoryOf(::GetPairedServersFlowUseCase)
        factoryOf(::GetRelayConfigurationFlowUseCase)
        factoryOf(::GetVisibleServersFlowUseCase)
        factoryOf(::UnpairDeviceUseCase)
    }
