package org.vpilo.babymonitor.app.client.home

import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import babymonitor.appcommon.generated.resources.Res
import babymonitor.appcommon.generated.resources.client_no_audio
import babymonitor.appcommon.generated.resources.client_pause_audio
import babymonitor.appcommon.generated.resources.client_play_audio
import babymonitor.appcommon.generated.resources.pause
import babymonitor.appcommon.generated.resources.play
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.vpilo.babymonitor.presentation.resources.capture_video_only
import org.vpilo.babymonitor.presentation.resources.Res as ResPresentation

@Composable
fun AudioFeed(
    modifier: Modifier = Modifier,
    canPlay: Boolean,
    isPlaying: Boolean,
    onToggle: () -> Unit,
) {
    Button(
        modifier = modifier,
        enabled = canPlay,
        onClick = onToggle,
    ) {
        val (icon, label) =
            when {
                !canPlay -> ResPresentation.drawable.capture_video_only to Res.string.client_no_audio
                isPlaying -> Res.drawable.pause to Res.string.client_pause_audio
                else -> Res.drawable.play to Res.string.client_play_audio
            }
        Icon(
            painter = painterResource(icon),
            contentDescription = stringResource(label),
        )
        Text(text = stringResource(label))
    }
}
