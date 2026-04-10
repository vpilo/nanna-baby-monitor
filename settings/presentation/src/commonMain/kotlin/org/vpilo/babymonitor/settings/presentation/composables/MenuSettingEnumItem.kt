package org.vpilo.babymonitor.settings.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppTheme
import org.vpilo.babymonitor.settings.model.EnumSetting
import org.vpilo.babymonitor.settings.presentation.preview.testSettingEnum

@Suppress("UNCHECKED_CAST")
@Composable
fun <T : Enum<*>> MenuSettingEnumItem(
    modifier: Modifier = Modifier,
    imageVector: ImageVector? = null,
    setting: EnumSetting<T>,
    onClick: (EnumSetting<T>) -> Unit = { },
    getValue: (EnumSetting<T>) -> T,
    setValue: (EnumSetting<T>, T) -> Unit,
) {
    check(setting.type.java.isEnum) { "Unsupported setting type ${setting.type} for setting ${setting.id}" }
    val value = getValue(setting)
    MenuItem(
        modifier = modifier,
        imageVector = imageVector,
        title = stringResource(checkNotNull(setting.name) { "Name cannot be null when displaying setting ${setting.id}" }),
        description = setting.description?.let { stringResource(it) },
        onClick = { onClick(setting) },
        bottomContent = {
            ThemedDropDownMenu(
                currentKey = value as Enum<*>,
                values = setting.values.map { it.key to stringResource(it.value) }.toMap(),
                onKeyChanged = { newKey -> setValue(setting, newKey as T) },
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
                imageVector = Icons.Default.SwapHoriz,
                setting = testSettingEnum,
                getValue = { it.default },
                setValue = { _, _ -> },
            )
        }
    }
