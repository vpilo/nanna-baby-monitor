package org.vpilo.babymonitor.app.server.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import kotlinx.coroutines.flow.flowOf
import org.koin.core.module.dsl.viewModelOf
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.camera.presentation.CameraViewFinder
import org.vpilo.babymonitor.camera.presentation.CameraViewFinderViewModel
import org.vpilo.babymonitor.model.CameraFrameFlow
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.AppDestination
import org.vpilo.babymonitor.presentation.composables.AppDestinationMainAction

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
        onMainActionClicked = onMenuClicked,
    ) {
        ServerHomeContent(
            modifier = modifier,
            isServerAvailable = state.isAvailable,
            captureMode = state.captureMode,
            onModeSelected = { viewModel.send(ServerHomeScreenAction.CaptureModeSelected(it)) },
        )
    }
}

@Composable
private fun ServerHomeContent(
    modifier: Modifier,
    isServerAvailable: Boolean,
    captureMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
) {
    Column(modifier = modifier) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            Text(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = Theme.Paddings.Tiny)
                        .zIndex(1f),
                text = if (isServerAvailable) "Available for connections." else "Server not available!",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isServerAvailable) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
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
            CameraViewFinder(
                captureMode = captureMode,
            )
        }
    }
}

@Preview
@Composable
private fun ServerHomeContentPreview() =
    AppPreviewTheme(
        withModule = {
            factory<VideoCaptureRepository> {
                object : VideoCaptureRepository {
                    override val frames: CameraFrameFlow = flowOf()
                }
            }
            viewModelOf(::CameraViewFinderViewModel)
        },
    ) {
        ServerHomeContent(
            modifier = Modifier,
            isServerAvailable = true,
            captureMode = CaptureMode.AUDIO_AND_VIDEO,
            onModeSelected = {},
        )
    }
