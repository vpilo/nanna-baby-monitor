package org.vpilo.babymonitor.network.model.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import org.vpilo.babymonitor.network.model.usecase.GetConnectableServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetNewServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedDevicesFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedNonVisibleServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedReachableServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetVisibleServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.IsSessionActiveFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.UnpairDeviceUseCase
import org.vpilo.babymonitor.network.model.usecase.UpdateServerNamesUseCase

val networkModelKoinModule: Module =
    module {
        factoryOf(::GetConnectableServersFlowUseCase)
        factoryOf(::GetNewServersFlowUseCase)
        factoryOf(::GetPairedDevicesFlowUseCase)
        factoryOf(::GetPairedNonVisibleServersFlowUseCase)
        factoryOf(::GetPairedServersFlowUseCase)
        factoryOf(::GetVisibleServersFlowUseCase)
        factoryOf(::GetPairedReachableServersFlowUseCase)
        factoryOf(::UpdateServerNamesUseCase)
        factoryOf(::UnpairDeviceUseCase)
        factoryOf(::IsSessionActiveFlowUseCase)
    }
