package org.vpilo.babymonitor.di

import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreenViewModel
import org.vpilo.babymonitor.app.cameraselection.CameraSelectionScreenViewModel
import org.vpilo.babymonitor.app.client.home.ClientHomeScreenViewModel
import org.vpilo.babymonitor.app.menu.MenuScreenViewModel
import org.vpilo.babymonitor.app.onboarding.OnboardingScreenViewModel
import org.vpilo.babymonitor.app.server.home.ServerHomeScreenViewModel
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
            viewModelOf(::CameraSelectionScreenViewModel)
            viewModelOf(::ClientHomeScreenViewModel)
            viewModelOf(::MenuScreenViewModel)
        },
        appPlatformModule,
    )
