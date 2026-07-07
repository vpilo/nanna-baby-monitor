package org.vpilo.babymonitor.camera.presentation.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import babymonitor.camera.presentation.generated.resources.Res
import babymonitor.camera.presentation.generated.resources.pairing_pin_label
import babymonitor.camera.presentation.generated.resources.pairing_pin_submit
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.Theme

// Placeholder pending Task 17, which replaces this with a QR-code scanner that also validates
// expectedDeviceId. Kept as a manual entry field for now purely so the module has an actual
// declaration for every target and the multiplatform build stays green.
@Composable
actual fun PinEntrySection(
    modifier: Modifier,
    expectedDeviceId: String,
    onPinEntered: (pin: String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium),
    ) {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.uppercase().take(PIN_LENGTH) },
            label = { Text(stringResource(Res.string.pairing_pin_label)) },
        )
        Button(
            enabled = pin.length == PIN_LENGTH,
            onClick = { onPinEntered(pin) },
        ) {
            Text(stringResource(Res.string.pairing_pin_submit))
        }
    }
}

private const val PIN_LENGTH = 6
