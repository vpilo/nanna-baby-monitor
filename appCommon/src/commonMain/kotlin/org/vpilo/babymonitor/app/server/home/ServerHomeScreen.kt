package org.vpilo.babymonitor.app.server.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_server_home
import babymonitor.appcommon.generated.resources.server_available_on_local_network
import babymonitor.appcommon.generated.resources.server_network_cloud
import babymonitor.appcommon.generated.resources.server_network_cloud_alert
import babymonitor.appcommon.generated.resources.server_network_local
import babymonitor.appcommon.generated.resources.server_network_local_alert
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.composables.PanningVideoFeed
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction
import org.vpilo.babymonitor.presentation.preview.makePreviewVideoStream

@Composable
fun ServerHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ServerHomeScreenViewModel,
    onMenuClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCancelled = { /* no-op */ },
        onBackCompleted = { onMenuClicked() },
    )

    AppDestination(
        title = Res.string.app_title_server_home,
        mainAction = AppDestinationMainAction.Menu,
        actions = {
            StatusIcons(
                isAvailableOnLocalNetwork = state.isAvailableOnLocalNetwork,
                hasRelay = state.isRelayConfigured,
                isAvailableOnRelay = state.isAvailableOnRelay,
            )
        },
        onMainActionClicked = onMenuClicked,
    ) {
        ServerHomeContent(
            modifier = modifier,
            captureMode = state.captureMode,
            videoStream = viewModel.videoStream,
            onModeSelected = { viewModel.send(ServerHomeScreenAction.CaptureModeSelected(it)) },
        )
    }
}

@Composable
private fun StatusIcons(
    isAvailableOnLocalNetwork: Boolean,
    hasRelay: Boolean,
    isAvailableOnRelay: Boolean,
) {
    Icon(
        painter =
            painterResource(
                if (isAvailableOnLocalNetwork) {
                    Res.drawable.server_network_local
                } else {
                    Res.drawable.server_network_local_alert
                },
            ),
        contentDescription = stringResource(Res.string.server_available_on_local_network),
    )

    if (!hasRelay) return
    Icon(
        painter =
            painterResource(
                if (isAvailableOnRelay) {
                    Res.drawable.server_network_cloud
                } else {
                    Res.drawable.server_network_cloud_alert
                },
            ),
        contentDescription = stringResource(Res.string.server_available_on_local_network),
    )
}

@Composable
private fun ServerHomeContent(
    modifier: Modifier,
    captureMode: CaptureMode,
    videoStream: OpaqueVideoStream,
    onModeSelected: (CaptureMode) -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        CaptureModeSelector(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = Theme.Paddings.Tiny)
                    .zIndex(2f),
            captureMode = captureMode,
            onModeSelected = onModeSelected,
        )
        PanningVideoFeed(
            videoStream = videoStream,
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
        )
    }
}

@Preview
@Composable
private fun ServerHomeContentPreview() =
    AppPreviewTheme {
        ServerHomeContent(
            modifier = Modifier,
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
            videoStream = makePreviewVideoStream(),
            onModeSelected = {},
        )
    }

@Preview
@Composable
private fun StatusIconsPreview() =
    AppPreviewTheme {
        Column {
            Row {
                StatusIcons(
                    isAvailableOnLocalNetwork = true,
                    hasRelay = true,
                    isAvailableOnRelay = true,
                )
            }
            Row {
                StatusIcons(
                    isAvailableOnLocalNetwork = false,
                    hasRelay = true,
                    isAvailableOnRelay = false,
                )
            }
            Row {
                StatusIcons(
                    isAvailableOnLocalNetwork = true,
                    hasRelay = false,
                    isAvailableOnRelay = true,
                )
            }
        }
    }
