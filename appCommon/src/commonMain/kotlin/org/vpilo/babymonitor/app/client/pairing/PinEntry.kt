package org.vpilo.babymonitor.app.client.pairing

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.client_pairing_pin_label
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.network.model.pairing.Pin
import org.vpilo.babymonitor.settings.model.Platform
import org.vpilo.babymonitor.settings.model.getCurrentPlatform

@Composable
fun PinEntry(
    pinResetKey: Any,
    modifier: Modifier = Modifier,
    onPinEntered: (pin: Pin) -> Unit,
) {
    var pin by remember(pinResetKey) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    OutlinedTextField(
        modifier =
            modifier
                .requiredHeight(IntrinsicSize.Min)
                .focusable()
                .focusRequester(focusRequester),
        value = pin,
        onValueChange = {
            pin = Pin.normalize(it)
            Pin
                .fromStringOrNull(pin)
                ?.let { validPin ->
                    onPinEntered(validPin)
                }
        },
        label = { Text(stringResource(Res.string.client_pairing_pin_label)) },
    )

    // On Android, keep the keyboard closed
    if (getCurrentPlatform() == Platform.Desktop) {
        SideEffect { focusRequester.requestFocus() }
    }
}
