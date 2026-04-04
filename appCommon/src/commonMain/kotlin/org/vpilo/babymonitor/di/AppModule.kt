package org.vpilo.babymonitor.di

import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_server_home
import babymonitor.appcommon.generated.resources.client_pause_audio
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.vpilo.babymonitor.app.approlechoice.AppRoleChoiceScreenViewModel
import org.vpilo.babymonitor.app.cameraselection.CameraSelectionScreenViewModel
import org.vpilo.babymonitor.app.client.ClientHomeScreenViewModel
import org.vpilo.babymonitor.app.server.ServerHomeScreenViewModel
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase
import org.vpilo.babymonitor.settings.model.PlatformAvailability
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.SettingCategory
import org.vpilo.babymonitor.settings.model.SettingRegistry
import kotlin.coroutines.CoroutineContext

expect val appPlatformModule: Module

val appSharedKoinModules =
    listOf(
        module {
            single<CoroutineContext> { Dispatchers.Default }

            SettingRegistry.register(
                Setting(
                    id = "test",
                    name = Res.string.client_pause_audio,
                    description = Res.string.app_title_server_home,
                    category = SettingCategory.General,
                    platform = PlatformAvailability.AllPlatforms,
                    type = String::class,
                    default = "default value",
                ),
            )

            factoryOf(::PlayReceivedAudioUseCase)

            viewModelOf(::AppRoleChoiceScreenViewModel)
            viewModelOf(::ServerHomeScreenViewModel)
            viewModelOf(::CameraSelectionScreenViewModel)
            viewModelOf(::ClientHomeScreenViewModel)
        },
        appPlatformModule,
    )
