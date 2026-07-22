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
import babymonitor.appcommon.generated.resources.paired_devices_revoke
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.settings.model.repository.PairedClient

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
            clients = state.clients,
            onRevoke = { viewModel.send(PairedDevicesScreenAction.Revoke(it)) },
        )
    }
}

@Composable
private fun PairedDevicesView(
    modifier: Modifier = Modifier,
    clients: List<PairedClient>,
    onRevoke: (clientId: String) -> Unit,
) {
    if (clients.isEmpty()) {
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
        items(clients, key = { it.clientId }) { client ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Theme.Paddings.Medium),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = client.name, style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { onRevoke(client.clientId) }) {
                    Text(text = stringResource(Res.string.paired_devices_revoke))
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
            clients =
                (1..5).map { idx ->
                    PairedClient(
                        clientId = "$idx",
                        name = "Device $idx",
                        sharedSecretBase64 = "secret",
                        pairedAtEpochMillis = 0L,
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
            clients = listOf(),
            onRevoke = {},
        )
    }
