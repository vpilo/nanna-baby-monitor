package org.vpilo.babymonitor.presentation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.koin.compose.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.module
import org.koin.mp.KoinPlatformTools
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository

@Composable
fun AppPreviewTheme(
    modifier: Modifier = Modifier,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
    withModule: (Module.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    AppTheme(modifier = modifier, useDarkTheme = useDarkTheme) {
        KoinPlatformTools
            .defaultContext()
            .getOrNull()
            ?.let {
                content()
                return@AppTheme
            }

        KoinApplication(
            application = {
                withModule?.let { extraModuleDefinition ->
                    modules(
                        module(true) {
                            single<SettingsRepository> {
                                object : SettingsRepository {
                                    override fun <T : Any> flowOf(setting: Setting<T>): Flow<T> = emptyFlow()

                                    override suspend fun <T : Any> load(setting: Setting<T>): T = error("")

                                    override suspend fun <T : Any> save(
                                        setting: Setting<T>,
                                        value: T,
                                    ) = Unit

                                    override fun <T : Any> saveDelayed(
                                        setting: Setting<T>,
                                        value: T,
                                    ) = Unit

                                    override suspend fun <T : Any> clear(setting: Setting<T>) = Unit
                                }
                            }

                            extraModuleDefinition()
                        },
                    )
                }
            },
        ) {
            content()
        }
    }
}
