package org.vpilo.babymonitor.app.server.home

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.server_mode_audio_and_video
import babymonitor.appcommon.generated.resources.server_mode_audio_only
import babymonitor.appcommon.generated.resources.server_mode_video_only
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.Tooltip
import org.vpilo.babymonitor.presentation.resources.capture_audio_and_video
import org.vpilo.babymonitor.presentation.resources.capture_audio_only
import org.vpilo.babymonitor.presentation.resources.capture_video_only
import org.vpilo.babymonitor.presentation.resources.Res as ResPresentation

@Composable
internal fun CaptureModeSelector(
    modifier: Modifier,
    captureMode: CaptureMode,
    onModeSelected: (CaptureMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = modifier,
    ) {
        CaptureMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                label = {
                    val (label, icon) =
                        when (mode) {
                            CaptureMode.AUDIO_AND_VIDEO -> {
                                Res.string.server_mode_audio_and_video to ResPresentation.drawable.capture_audio_and_video
                            }

                            CaptureMode.VIDEO_ONLY -> {
                                Res.string.server_mode_video_only to ResPresentation.drawable.capture_video_only
                            }

                            CaptureMode.AUDIO_ONLY -> {
                                Res.string.server_mode_audio_only to ResPresentation.drawable.capture_audio_only
                            }
                        }
                    Tooltip(text = stringResource(label)) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                        )
                    }
                },
                shape =
                    SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = CaptureMode.entries.size,
                    ),
                colors =
                    SegmentedButtonDefaults.colors(
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                selected = mode == captureMode,
                onClick = { onModeSelected(mode) },
            )
        }
    }
}

@Preview
@Composable
private fun CaptureModeSelectorAudioAndVideoPreview() = CaptureModeSelectorPreviewContent(captureMode = CaptureMode.AUDIO_AND_VIDEO)

@Preview
@Composable
private fun CaptureModeSelectorVideoOnlyPreview() = CaptureModeSelectorPreviewContent(captureMode = CaptureMode.VIDEO_ONLY)

@Preview
@Composable
private fun CaptureModeSelectorAudioOnlyPreview() = CaptureModeSelectorPreviewContent(captureMode = CaptureMode.AUDIO_ONLY)

@Preview
@Composable
private fun CaptureModeSelectorAudioAndVideoDarkPreview() =
    CaptureModeSelectorPreviewContent(captureMode = CaptureMode.AUDIO_AND_VIDEO, useDarkTheme = true)

@Preview
@Composable
private fun CaptureModeSelectorVideoOnlyDarkPreview() =
    CaptureModeSelectorPreviewContent(captureMode = CaptureMode.VIDEO_ONLY, useDarkTheme = true)

@Preview
@Composable
private fun CaptureModeSelectorAudioOnlyDarkPreview() =
    CaptureModeSelectorPreviewContent(captureMode = CaptureMode.AUDIO_ONLY, useDarkTheme = true)

@Composable
private fun CaptureModeSelectorPreviewContent(
    captureMode: CaptureMode,
    useDarkTheme: Boolean = false,
) = AppPreviewTheme(useDarkTheme = useDarkTheme) {
    CaptureModeSelector(
        modifier = Modifier,
        captureMode = captureMode,
        onModeSelected = {},
    )
}
