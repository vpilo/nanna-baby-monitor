package org.vpilo.babymonitor.presentation.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.painterResource
import org.vpilo.babymonitor.presentation.AppPreviewTheme
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.PulseAnimation
import org.vpilo.babymonitor.presentation.resources.Res
import org.vpilo.babymonitor.presentation.resources.recording

@Composable
fun RecordingIcon(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = Box(
    modifier =
        modifier
            .size(Theme.Sizes.Button),
) {
    Icon(
        modifier = Modifier.align(Alignment.Center),
        painter = painterResource(Res.drawable.recording),
        tint = if (enabled) Color.Red else Color.Gray,
        contentDescription = null,
    )
    if (enabled) {
        PulseAnimation(
            modifier = modifier,
            color = Color(0xFF990000),
        )
    }
}

@Preview
@Composable
private fun RecordingIconPreview() =
    AppPreviewTheme {
        RecordingIcon()
    }

@Preview
@Composable
private fun RecordingIconDisabledPreview() =
    AppPreviewTheme {
        RecordingIcon(enabled = false)
    }
