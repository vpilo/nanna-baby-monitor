package org.vpilo.babymonitor.app.server.paireddevices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_paired_devices
import babymonitor.appcommon.generated.resources.paired_devices_empty
import babymonitor.appcommon.generated.resources.paired_devices_revoke
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.ScrollableBox
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
        PairedDevicesContent(
            modifier = Modifier.fillMaxSize(),
            clients = state.clients,
            onRevoke = { viewModel.send(PairedDevicesScreenAction.Revoke(it)) },
        )
    }
}

@Composable
private fun PairedDevicesContent(
    modifier: Modifier,
    clients: List<PairedClient>,
    onRevoke: (clientId: String) -> Unit,
) {
    if (clients.isEmpty()) {
        ScrollableBox(modifier = modifier) {
            Text(
                modifier = Modifier.padding(Theme.Paddings.Medium),
                text = stringResource(Res.string.paired_devices_empty),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    LazyColumn(modifier = modifier) {
        items(clients, key = { it.clientId }) { client ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Theme.Paddings.Medium, vertical = Theme.Paddings.Small),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = client.name, style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { onRevoke(client.clientId) }) {
                    Text(text = stringResource(Res.string.paired_devices_revoke))
                }
            }
        }
    }
}
