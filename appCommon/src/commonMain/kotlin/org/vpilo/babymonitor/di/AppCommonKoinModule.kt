package org.vpilo.babymonitor.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreenViewModel
import org.vpilo.babymonitor.app.cameraselection.CameraSelectionScreenViewModel
import org.vpilo.babymonitor.app.client.home.ClientHomeScreenViewModel
import org.vpilo.babymonitor.app.client.pairing.ClientPairingScreenViewModel
import org.vpilo.babymonitor.app.menu.MenuScreenViewModel
import org.vpilo.babymonitor.app.onboarding.OnboardingScreenViewModel
import org.vpilo.babymonitor.app.server.home.ServerHomeScreenViewModel
import org.vpilo.babymonitor.app.server.paireddevices.PairedDevicesScreenViewModel
import org.vpilo.babymonitor.app.server.pairing.ServerPairingScreenViewModel
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase
import org.vpilo.babymonitor.settings.model.usecase.GetLocalClientDeviceFlowUseCase
import kotlin.coroutines.CoroutineContext

expect val appPlatformModule: Module

val appSharedKoinModules =
    listOf(
        module {
            single<CoroutineContext> { Dispatchers.Default }

            factoryOf(::PlayReceivedAudioUseCase)
            factoryOf(::GetLocalClientDeviceFlowUseCase)

            viewModelOf(::OnboardingScreenViewModel)
            viewModelOf(::AppRoleChoiceScreenViewModel)
            viewModelOf(::ServerHomeScreenViewModel)
            viewModelOf(::ServerPairingScreenViewModel)
            viewModelOf(::PairedDevicesScreenViewModel)
            viewModelOf(::ClientHomeScreenViewModel)
            viewModelOf(::MenuScreenViewModel)

            viewModel { params ->
                CameraSelectionScreenViewModel(
                    deviceId = params.getOrNull(),
                    networkClientRepository = get(),
                    settingsRepository = get(),
                    getPairedNonVisibleServersFlowUseCase = get(),
                    getConnectableServersFlowUseCase = get(),
                    getNewServersFlowUseCase = get(),
                    localDiscoveryRepository = get(),
                    remoteDiscoveryRepository = get(),
                    deviceStateRepository = get(),
                    pairingRepository = get(),
                    getLocalClientDeviceFlowUseCase = get(),
                )
            }

            viewModel { params ->
                ClientPairingScreenViewModel(
                    deviceId = params.get(),
                    networkClientRepository = get(),
                    localDiscoveryRepository = get(),
                )
            }
        },
        appPlatformModule,
    )
