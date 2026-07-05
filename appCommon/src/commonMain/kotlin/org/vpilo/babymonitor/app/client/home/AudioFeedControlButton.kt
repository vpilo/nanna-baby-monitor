package org.vpilo.babymonitor.app.client.home

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.audio_disabled
import babymonitor.appcommon.generated.resources.audio_playing
import babymonitor.appcommon.generated.resources.audio_stopped
import babymonitor.appcommon.generated.resources.client_no_audio
import babymonitor.appcommon.generated.resources.client_pause_audio
import babymonitor.appcommon.generated.resources.client_play_audio
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.composables.Tooltip

@Composable
fun AudioFeedControlButton(
    modifier: Modifier = Modifier,
    canPlay: Boolean,
    isPlaying: Boolean,
    onToggle: () -> Unit,
) {
    val (icon, label) =
        when {
            !canPlay -> Res.drawable.audio_disabled to Res.string.client_no_audio
            isPlaying -> Res.drawable.audio_playing to Res.string.client_pause_audio
            else -> Res.drawable.audio_stopped to Res.string.client_play_audio
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
private fun AudioFeedControlButtonPlayingPreview() {
    AppPreviewTheme {
        AudioFeedControlButton(
            canPlay = true,
            isPlaying = true,
            onToggle = {},
        )
    }
}

@Preview
@Composable
private fun AudioFeedControlButtonStoppedPreview() {
    AppPreviewTheme(useDarkTheme = true) {
        AudioFeedControlButton(
            canPlay = true,
            isPlaying = false,
            onToggle = {},
        )
    }
}

@Preview
@Composable
private fun AudioFeedControlButtonDisabledPreview() {
    AppPreviewTheme {
        AudioFeedControlButton(
            canPlay = false,
            isPlaying = false,
            onToggle = {},
        )
    }
}
