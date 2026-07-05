package org.vpilo.babymonitor.presentation.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.resources.Res
import org.vpilo.babymonitor.presentation.resources.network_available_on_local_network
import org.vpilo.babymonitor.presentation.resources.network_available_on_relay
import org.vpilo.babymonitor.presentation.resources.network_cloud
import org.vpilo.babymonitor.presentation.resources.network_cloud_alert
import org.vpilo.babymonitor.presentation.resources.network_local
import org.vpilo.babymonitor.presentation.resources.network_local_alert
import org.vpilo.babymonitor.presentation.resources.network_unavailable_on_local_network
import org.vpilo.babymonitor.presentation.resources.network_unavailable_on_relay

@Composable
fun ConnectionStatusIcons(
    modifier: Modifier = Modifier,
    hasRelay: Boolean,
    isOnLocalNetwork: Boolean,
    isOnRelay: Boolean,
) {
    Row(modifier = modifier) {
        val (localNetworkIcon, localNetworkLabel) =
            if (isOnLocalNetwork) {
                painterResource(Res.drawable.network_local) to stringResource(Res.string.network_available_on_local_network)
            } else {
                painterResource(Res.drawable.network_local_alert) to stringResource(Res.string.network_unavailable_on_local_network)
            }
        Tooltip(text = localNetworkLabel) {
            Icon(painter = localNetworkIcon, contentDescription = null)
        }

        if (!hasRelay) return
        val (relayIcon, relayLabel) =
            if (isOnRelay) {
                painterResource(Res.drawable.network_cloud) to stringResource(Res.string.network_available_on_relay)
            } else {
                painterResource(Res.drawable.network_cloud_alert) to stringResource(Res.string.network_unavailable_on_relay)
            }
        Tooltip(text = relayLabel) {
            Icon(painter = relayIcon, contentDescription = null)
        }
    }
}

@Preview
@Composable
private fun ConnectionStatusIconsPreview() =
    AppPreviewTheme {
        Column {
            Row {
                ConnectionStatusIcons(
                    hasRelay = true,
                    isOnLocalNetwork = true,
                    isOnRelay = true,
                )
            }
            Row {
                ConnectionStatusIcons(
                    hasRelay = true,
                    isOnLocalNetwork = false,
                    isOnRelay = false,
                )
            }
            Row {
                ConnectionStatusIcons(
                    hasRelay = false,
                    isOnLocalNetwork = true,
                    isOnRelay = true,
                )
            }
        }
    }
