package org.vpilo.babymonitor.app.client.pairing

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.requiredHeight
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
import org.jetbrains.compose.resources.stringResource

@Composable
fun PinEntry(
    pinResetKey: Any,
    modifier: Modifier = Modifier,
    pairingPinLength: Int,
    onPinEntered: (pin: String) -> Unit,
) {
    var pin by remember(pinResetKey) { mutableStateOf("") }

    OutlinedTextField(
        modifier = modifier.requiredHeight(IntrinsicSize.Min),
        value = pin,
        onValueChange = {
            pin = it.uppercase().take(pairingPinLength)
            if (pin.length == pairingPinLength) {
                onPinEntered(pin)
            }
        },
        label = { Text(stringResource(Res.string.client_pairing_pin_label)) },
    )
}
