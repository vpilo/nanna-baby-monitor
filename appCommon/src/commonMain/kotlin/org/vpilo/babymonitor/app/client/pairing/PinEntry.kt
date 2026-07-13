package org.vpilo.babymonitor.app.client.pairing

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
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.client_pairing_pin_label
import babymonitor.appcommon.generated.resources.client_pairing_pin_submit
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun PinEntry(
    modifier: Modifier,
    onPinEntered: (pin: String, deviceId: String?) -> Unit,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium),
    ) {
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it.uppercase().take(PIN_LENGTH) },
            label = { Text(stringResource(Res.string.client_pairing_pin_label)) },
        )
        Button(
            enabled = pin.length == PIN_LENGTH,
            onClick = { onPinEntered(pin, null) },
        ) {
            Text(stringResource(Res.string.client_pairing_pin_submit))
        }
    }
}

// VALERIO share by moving somewhere else
private const val PIN_LENGTH = 6
