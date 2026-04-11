package org.vpilo.babymonitor.settings.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.settings.model.EnumSetting
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.presentation.preview.testSettingEnum

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Enum<*>> MenuSettingEnumItem(
    modifier: Modifier = Modifier,
    setting: Setting<T>,
    imageVector: ImageVector? = null,
) {
    check(setting is EnumSetting<T>) { "Setting ${setting.id} is not an EnumSetting" }
    check(setting.type.java.isEnum) { "Unsupported setting type ${setting.type} for setting ${setting.id}" }

    val viewModel: MenuSettingItemViewModel =
        koinViewModel(
            key = setting.id.value,
            parameters = { parametersOf(setting) },
        )
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    MenuItem(
        modifier = modifier,
        imageVector = imageVector,
        title = stringResource(checkNotNull(setting.name) { "Name cannot be null when displaying setting ${setting.id}" }),
        description = setting.description?.let { stringResource(it) },
        bottomContent = {
            ThemedDropDownMenu(
                currentKey = state.value as Enum<*>,
                values = setting.values.map { it.key to stringResource(it.value) }.toMap(),
                onKeyChanged = { newKey -> viewModel.send(MenuSettingItemAction.SetValue(newKey)) },
            )
        },
    )
}

@Preview
@Composable
private fun MenuSettingEnumItemPreview() =
    AppTheme {
        Column {
            MenuSettingEnumItem(
                setting = testSettingEnum,
                imageVector = Icons.Default.SwapHoriz,
            )
        }
    }
