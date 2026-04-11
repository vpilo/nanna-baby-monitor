package org.vpilo.babymonitor.settings.presentation.di

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.vpilo.babymonitor.settings.presentation.composables.MenuSettingItemViewModel

val settingsPresentationKoinModule =
    module {
        viewModel { params ->
            MenuSettingItemViewModel(
                settingsRepository = get(),
                setting = params.get(),
            )
        }
    }
