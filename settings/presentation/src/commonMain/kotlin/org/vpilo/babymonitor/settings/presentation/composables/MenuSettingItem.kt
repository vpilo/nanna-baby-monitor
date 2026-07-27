package org.vpilo.babymonitor.settings.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataThresholding
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.settings.presentation.generated.resources.Res
import babymonitor.settings.presentation.generated.resources.example
import babymonitor.settings.presentation.generated.resources.setting_int_current_value
import babymonitor.settings.presentation.generated.resources.setting_int_decrement
import babymonitor.settings.presentation.generated.resources.setting_int_increment
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.settings.model.PrimitiveSetting
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

    checkNotNull(setting as? PrimitiveSetting<T>) {
        "MenuSettingItem only supports PrimitiveSetting, but got ${setting::class.simpleName}"
    }

    MenuItem(
        modifier = modifier,
        imageVector = imageVector,
        title = stringResource(checkNotNull(setting.name) { "Name cannot be null when displaying setting ${setting.id}" }),
        description = setting.description?.let { stringResource(it) },
        endContent = {
            when (setting.type) {
                Boolean::class -> {
                    ThemedSwitch(
                        checked = state.value as Boolean,
                        onCheckedChange = {
                            viewModel.send(MenuSettingItemAction.SetValue(it))
                        },
                    )
                }

                else -> {}
            }
        },
        bottomContent = {
            when (setting.type) {
                String::class -> {
                    TextField(
                        modifier = Modifier.fillMaxWidth(.8f),
                        value = state.value as String,
                        singleLine = true,
                        onValueChange = {
                            viewModel.send(MenuSettingItemAction.SetValue(it))
                        },
                    )
                }

                Int::class if setting.limits != null -> {
                    val range = checkNotNull(setting.limits).let { it.first.toFloat()..it.last.toFloat() }
                    Logger.w("MenuSettingItem") { "Using Slider for Int setting ${setting.id} with limits $range" }
                    Slider(
                        modifier = Modifier.fillMaxWidth(.95f),
                        value = (state.value as Int).toFloat(),
                        valueRange = range,
                        steps = (range.endInclusive - range.start - 1).toInt(),
                        onValueChange = {
                            viewModel.send(MenuSettingItemAction.SetValue(it.toInt()))
                        },
                    )
                    Text(
                        text = stringResource(Res.string.setting_int_current_value, state.value as Int),
                    )
                }

                Int::class -> {
                    val value = state.value as Int
                    val onValueChange = { newValue: Int ->
                        viewModel.send(MenuSettingItemAction.SetValue(newValue))
                    }
                    TextField(
                        modifier = Modifier.fillMaxWidth(.3f),
                        value = value.toString(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
                        onValueChange = {
                            onValueChange(it.toIntOrNull() ?: 0)
                        },
                        trailingIcon = {
                            Column {
                                IconButton(
                                    onClick = { onValueChange(value + 1) },
                                    modifier = Modifier.size(Theme.Sizes.IconSmall),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowUp,
                                        contentDescription = stringResource(Res.string.setting_int_increment),
                                    )
                                }
                                IconButton(
                                    onClick = { onValueChange(value - 1) },
                                    modifier = Modifier.size(Theme.Sizes.IconSmall),
                                ) {
                                    Icon(
                                        Icons.Default.KeyboardArrowDown,
                                        contentDescription = stringResource(Res.string.setting_int_decrement),
                                    )
                                }
                            }
                        },
                    )
                }

                else -> {}
            }
        },
    )
}

@Preview
@Composable
private fun MenuSettingItemPreview() =
    AppPreviewTheme(
        withModule = {
            viewModelOf(::MenuSettingItemViewModel)
        },
    ) {
        Column {
            val testBool =
                Setting.makeBoolean(
                    id = SettingId("notifications"),
                    name = Res.string.example,
                    description = Res.string.example,
                    default = false,
                )
            val testString =
                Setting.makeString(
                    id = SettingId("device_name"),
                    name = Res.string.example,
                    description = Res.string.example,
                    default = "Baby Bedroom",
                )
            val testIntUnlimited =
                Setting.makeInt(
                    id = SettingId("threshold_unlimited"),
                    name = Res.string.example,
                    description = Res.string.example,
                    default = 1,
                )
            val testIntLimited =
                Setting.makeInt(
                    id = SettingId("threshold_limited"),
                    name = Res.string.example,
                    description = Res.string.example,
                    default = 5,
                    limits = 1..10,
                )
            MenuSettingItem(
                imageVector = Icons.Default.Notifications,
                setting = testBool,
            )
            MenuSettingItem(imageVector = Icons.Default.SwapHoriz, setting = testString)
            MenuSettingItem(imageVector = Icons.Default.Numbers, setting = testIntUnlimited)
            MenuSettingItem(imageVector = Icons.Default.DataThresholding, setting = testIntLimited)
        }
    }
