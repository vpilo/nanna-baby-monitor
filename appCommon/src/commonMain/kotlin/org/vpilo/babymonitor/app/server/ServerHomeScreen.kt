package org.vpilo.babymonitor.app.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.capture_audio_and_video
import babymonitor.appcommon.generated.resources.capture_audio_only
import babymonitor.appcommon.generated.resources.capture_video_only
import babymonitor.appcommon.generated.resources.server_mode_audio_and_video
import babymonitor.appcommon.generated.resources.server_mode_audio_only
import babymonitor.appcommon.generated.resources.server_mode_video_only
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.camera.presentation.CameraViewFinder
import org.vpilo.babymonitor.model.CaptureMode

@Composable
fun ServerHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ServerHomeScreenViewModel,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    ServerHomeContent(
        modifier = modifier,
        isServerAvailable = state.isAvailable,
        captureMode = state.captureMode,
        onModeSelected = { viewModel.send(ServerHomeScreenAction.CaptureModeSelected(it)) },
    )
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
                modifier = Modifier.align(Alignment.TopEnd),
                captureMode = captureMode,
                onModeSelected = onModeSelected,
            )
            CameraViewFinder()
        }
    }
}

@Composable
private fun CaptureModeSelector(
    modifier: Modifier,
    captureMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        CaptureMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                label = {
                    val (label, icon) = when (mode) {
                        CaptureMode.AUDIO_AND_VIDEO ->
                            Res.string.server_mode_audio_and_video to Res.drawable.capture_audio_and_video
                        CaptureMode.VIDEO_ONLY ->
                            Res.string.server_mode_video_only to Res.drawable.capture_video_only
                        CaptureMode.AUDIO_ONLY ->
                            Res.string.server_mode_audio_only to Res.drawable.capture_audio_only
                    }
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = stringResource(label),
                    )
                },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = CaptureMode.entries.size,
                ),
                selected = mode == captureMode,
                onClick = { onModeSelected(mode) },
            )
        }
    }
}
