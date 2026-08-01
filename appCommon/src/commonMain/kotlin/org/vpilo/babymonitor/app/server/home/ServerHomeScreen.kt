package org.vpilo.babymonitor.app.server.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import babymonitor.appcommon.generated.resources.app_title_server_pairing
import babymonitor.appcommon.generated.resources.pair
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.composables.PanningVideoFeed
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.OpaqueVideoStream
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction
import org.vpilo.babymonitor.presentation.composables.ConnectionStatusIcons
import org.vpilo.babymonitor.presentation.composables.LoadingBox
import org.vpilo.babymonitor.presentation.composables.Tooltip
import org.vpilo.babymonitor.presentation.composables.rememberIsVideoFeedActive
import org.vpilo.babymonitor.presentation.preview.makePreviewVideoStream
import org.vpilo.babymonitor.presentation.server.RecordingIcon

@Composable
fun ServerHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ServerHomeScreenViewModel,
    onMenuClicked: () -> Unit,
    onPairClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = true,
        onBackCancelled = { /* no-op */ },
        onBackCompleted = { onMenuClicked() },
    )

    AppDestination(
        title = stringResource(Res.string.app_title_server_home, state.name),
        mainAction = AppDestinationMainAction.Menu,
        actions = {
            Tooltip(text = stringResource(Res.string.app_title_server_pairing, state.name)) {
                IconButton(onClick = onPairClicked) {
                    Icon(painter = painterResource(Res.drawable.pair), contentDescription = null)
                }
            }
            ConnectionStatusIcons(
                hasRelay = state.isRelayConfigured,
                isOnLocalNetwork = state.isAvailableOnLocalNetwork,
                isOnRelay = state.isAvailableOnRelay,
            )
        },
        onMainActionClicked = onMenuClicked,
    ) {
        ServerHomeContent(
            modifier = modifier,
            isRecording = state.isAvailableOnLocalNetwork || state.isAvailableOnRelay,
            captureMode = state.captureMode,
            videoStream = viewModel.videoStream,
            onModeSelected = { viewModel.send(ServerHomeScreenAction.CaptureModeSelected(it)) },
        )
    }
}

@Composable
private fun ServerHomeContent(
    modifier: Modifier,
    isRecording: Boolean,
    captureMode: CaptureMode,
    videoStream: OpaqueVideoStream,
    onModeSelected: (CaptureMode) -> Unit,
) {
    val isFeedActive = rememberIsVideoFeedActive(videoStream).value
    Box(modifier = modifier.fillMaxSize()) {
        RecordingIcon(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .zIndex(2f),
            enabled = isFeedActive && isRecording,
        )
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
        if (captureMode != CaptureMode.AUDIO_ONLY && !isFeedActive) {
            LoadingBox()
        }
    }
}

@Preview
@Composable
private fun ServerHomeContentPreview() =
    AppPreviewTheme {
        ServerHomeContent(
            modifier = Modifier,
            isRecording = true,
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
            videoStream = makePreviewVideoStream(),
            onModeSelected = {},
        )
    }
