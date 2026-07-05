package org.vpilo.babymonitor.app.client.home

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.client_no_video
import babymonitor.appcommon.generated.resources.client_pause_video
import babymonitor.appcommon.generated.resources.client_play_video
import babymonitor.appcommon.generated.resources.video_disabled
import babymonitor.appcommon.generated.resources.video_playing
import babymonitor.appcommon.generated.resources.video_stopped
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.Tooltip

@Composable
fun VideoFeedControlButton(
    modifier: Modifier = Modifier,
    canPlay: Boolean,
    isPlaying: Boolean,
    onToggle: () -> Unit,
) {
    val (icon, label) =
        when {
            !canPlay -> Res.drawable.video_disabled to Res.string.client_no_video
            isPlaying -> Res.drawable.video_playing to Res.string.client_pause_video
            else -> Res.drawable.video_stopped to Res.string.client_play_video
        }
    Tooltip(text = stringResource(label)) {
        IconButton(
            modifier = modifier,
            enabled = canPlay,
            onClick = onToggle,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
        }
    }
}

@Preview
@Composable
private fun VideoFeedControlButtonPlayingPreview() {
    AppPreviewTheme {
        VideoFeedControlButton(
            canPlay = true,
            isPlaying = true,
            onToggle = {},
        )
    }
}

@Preview
@Composable
private fun VideoFeedControlButtonStoppedPreview() {
    AppPreviewTheme(useDarkTheme = true) {
        VideoFeedControlButton(
            canPlay = true,
            isPlaying = false,
            onToggle = {},
        )
    }
}

@Preview
@Composable
private fun VideoFeedControlButtonDisabledPreview() {
    AppPreviewTheme {
        VideoFeedControlButton(
            canPlay = false,
            isPlaying = false,
            onToggle = {},
        )
    }
}
