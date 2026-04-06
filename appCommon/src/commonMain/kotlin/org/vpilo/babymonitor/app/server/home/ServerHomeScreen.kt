package org.vpilo.babymonitor.app.server.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.app_title_server_home
import org.vpilo.babymonitor.camera.presentation.CameraViewFinder
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.presentation.composables.AppDestination

@Composable
fun ServerHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ServerHomeScreenViewModel,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    AppDestination(
        title = Res.string.app_title_server_home,
        onBackClicked = onBackClicked,
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
        Text(
            text = if (isServerAvailable) "Available for connections." else "Server not available!",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isServerAvailable) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
        )
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
            CaptureModeSelector(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .zIndex(1f),
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
private fun ServerHomeContentPreview() {
    ServerHomeContent(
        modifier = Modifier.fillMaxSize(),
        isServerAvailable = true,
        captureMode = CaptureMode.AUDIO_AND_VIDEO,
        onModeSelected = {},
    )
}
