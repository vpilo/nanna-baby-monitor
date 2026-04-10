package org.vpilo.babymonitor.settings.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.presentation.AppTheme

@Composable
inline fun <reified E : Enum<*>> ThemedDropDownMenu(
    modifier: Modifier = Modifier,
    currentKey: E,
    values: Map<E, String>,
    crossinline onKeyChanged: (E) -> Unit,
    expanded: Boolean = false,
) {
    val constants = checkNotNull(values.keys) { "Unable to obtain values from enum class for dropdown" }
    ThemedDropDownMenu(
        modifier = modifier,
        currentValue = values[currentKey] ?: currentKey.name,
        values = values.map { it.key.name to it.value }.toMap(),
        onValueChanged = { newValue ->
            onKeyChanged(constants.first { it.name == newValue })
        },
        expanded = expanded,
    )
}

@Composable
fun ThemedDropDownMenu(
    modifier: Modifier = Modifier,
    currentValue: String,
    values: Map<String, String>,
    onValueChanged: (String) -> Unit,
    expanded: Boolean = false,
) {
    var isExpanded by remember { mutableStateOf(expanded) }

    @OptIn(ExperimentalMaterial3Api::class)
    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = isExpanded,
        onExpandedChange = { isExpanded = !isExpanded },
    ) {
        TextField(
            value = values[currentValue] ?: currentValue,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            values.forEach { (key, text) ->
                DropdownMenuItem(
                    text = { Text(text = text) },
                    onClick = {
                        onValueChanged(key)
                        isExpanded = false
                    },
                )
            }
        }
    }
}

@Preview
@Composable
private fun ThemedDropDownMenuStringPreview() =
    AppTheme {
        Column {
            Text(
                text = "Enable Notifications",
                style = MaterialTheme.typography.titleSmall,
            )
            ThemedDropDownMenu(expanded = false, currentValue = "Test 1", values = emptyMap(), onValueChanged = {})
            Text(
                text = "Enable Dark Mode",
                style = MaterialTheme.typography.titleSmall,
            )
            ThemedDropDownMenu(
                expanded = false,
                currentValue = "2",
                values = mapOf("1" to "Test 1", "2" to "Test 2", "3" to "Test 3"),
                onValueChanged = {},
            )
        }
    }

@Preview
@Composable
private fun ThemedDropDownMenuEnumPreview() =
    AppTheme {
        Column {
            Text(
                text = "Capture Mode",
                style = MaterialTheme.typography.titleSmall,
            )
            ThemedDropDownMenu(
                expanded = true,
                currentKey = CaptureMode.VIDEO_ONLY,
                values =
                    mapOf(
                        CaptureMode.VIDEO_ONLY to "Video Only",
                        CaptureMode.AUDIO_ONLY to "Audio Only",
                        CaptureMode.AUDIO_AND_VIDEO to "Video and Audio",
                    ),
                onKeyChanged = {},
            )
            Spacer(modifier = Modifier.height(250.dp))
        }
    }
