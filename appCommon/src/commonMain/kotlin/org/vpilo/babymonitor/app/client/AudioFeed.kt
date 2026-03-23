package org.vpilo.babymonitor.app.client

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AudioFeed(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    onToggle: () -> Unit,
) {
    Button(
        modifier = modifier,
        onClick = onToggle,
    ) {
        if (isPlaying) Text("Stop audio feed") else Text("Start audio feed")
    }
}
