package org.vpilo.babymonitor.settings.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.settings.presentation.generated.resources.Res
import babymonitor.settings.presentation.generated.resources.example
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.settings.model.Setting

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Any> MenuSettingItem(
    modifier: Modifier = Modifier,
    setting: Setting<T>,
    imageVector: ImageVector? = null,
) {
    val viewModel: MenuSettingItemViewModel =
        koinViewModel(
            key = setting.id.value,
            parameters = { parametersOf(setting) },
        )
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()
    val value = state.value as T

    MenuItem(
        modifier = modifier,
        imageVector = imageVector,
        title = stringResource(checkNotNull(setting.name) { "Name cannot be null when displaying setting ${setting.id}" }),
        description = setting.description?.let { stringResource(it) },
        endContent = {
            when (setting.type) {
                Boolean::class -> {
                    ThemedSwitch(
                        checked = value as Boolean,
                        onCheckedChange = { viewModel.send(MenuSettingItemAction.SetValue(it)) },
                    )
                }

                else -> { }
            }
        },
        bottomContent = {
            when (setting.type) {
                String::class -> {
                    TextField(
                        value = value as String,
                        onValueChange = { viewModel.send(MenuSettingItemAction.SetValue(it)) },
                    )
                }

                else -> { }
            }
        },
    )
}

@Preview
@Composable
private fun MenuSettingItemPreview() =
    AppTheme {
        Column {
            val testBool =
                Setting.makePrimitive(
                    id = SettingId("notifications"),
                    name = Res.string.example,
                    description = Res.string.example,
                    default = false,
                )
            val testString =
                Setting.makePrimitive(
                    id = SettingId("device_name"),
                    name = Res.string.example,
                    description = Res.string.example,
                    default = "Baby Bedroom",
                )
            MenuSettingItem(
                imageVector = Icons.Default.Notifications,
                setting = testBool,
            )
            MenuSettingItem(imageVector = Icons.Default.SwapHoriz, setting = testString)
        }
    }
