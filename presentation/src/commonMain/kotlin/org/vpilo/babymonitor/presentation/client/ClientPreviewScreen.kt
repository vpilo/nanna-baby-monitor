package org.vpilo.babymonitor.presentation.client

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.vpilo.babymonitor.camera.presentation.CameraView
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun ClientPreviewScreenRoot(
    modifier: Modifier = Modifier,
) {
    Column {
        Text(
            text = "Monitor",
            style = MaterialTheme.typography.titleMedium,
            color = Theme.Colors.text,
        )
        Box(modifier = modifier.fillMaxSize()) {
            CameraView()
        }
    }
}
