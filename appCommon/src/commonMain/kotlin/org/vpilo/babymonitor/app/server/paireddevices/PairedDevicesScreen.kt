package org.vpilo.babymonitor.app.server.paireddevices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_paired_devices
import babymonitor.appcommon.generated.resources.paired_devices_empty
import babymonitor.appcommon.generated.resources.paired_devices_unpair
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination

@Composable
fun PairedDevicesScreen(
    modifier: Modifier = Modifier,
    viewModel: PairedDevicesScreenViewModel,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    AppDestination(
        modifier = modifier,
        title = Res.string.app_title_paired_devices,
        onMainActionClicked = onBackClicked,
    ) {
        PairedDevicesView(
            modifier = Modifier.fillMaxSize(),
            devices = state.devices,
            onRevoke = { viewModel.send(PairedDevicesScreenAction.Revoke(it)) },
        )
    }
}

@Composable
private fun PairedDevicesView(
    modifier: Modifier = Modifier,
    devices: List<Device>,
    onRevoke: (clientId: String) -> Unit,
) {
    if (devices.isEmpty()) {
        Box(
            modifier = modifier.padding(Theme.Paddings.Medium),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = stringResource(Res.string.paired_devices_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(Theme.Paddings.Medium),
        verticalArrangement = Arrangement.spacedBy(Theme.Paddings.Medium),
    ) {
        items(devices, key = { it.id }) { device ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Theme.Paddings.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = device.name, style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { onRevoke(device.id.toString()) }) {
                    Text(text = stringResource(Res.string.paired_devices_unpair))
                }
            }
            HorizontalDivider()
        }
    }
}

@Preview
@Composable
private fun PairedDevicesViewPreview() =
    AppPreviewTheme {
        PairedDevicesView(
            modifier = Modifier.fillMaxSize(),
            devices =
                (1..5).map { idx ->
                    Device.Client(
                        id = DeviceId.random(),
                        name = "Device $idx",
                    )
                },
            onRevoke = {},
        )
    }

@Preview
@Composable
private fun PairedDevicesViewEmptyPreview() =
    AppPreviewTheme {
        PairedDevicesView(
            modifier = Modifier.fillMaxSize(),
            devices = listOf(),
            onRevoke = {},
        )
    }
