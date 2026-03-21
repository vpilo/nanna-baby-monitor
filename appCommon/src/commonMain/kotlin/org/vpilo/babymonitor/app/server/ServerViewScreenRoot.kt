package org.vpilo.babymonitor.app.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.vpilo.babymonitor.camera.presentation.CameraViewFinder
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun ServerViewScreenRoot(
    modifier: Modifier = Modifier,
) {
    Column {
        Text(
            text = "Camera",
            style = MaterialTheme.typography.titleMedium,
            color = Theme.Colors.text,
        )
        Box(modifier = modifier.fillMaxSize()) {
            CameraViewFinder()
        }
    }
}
