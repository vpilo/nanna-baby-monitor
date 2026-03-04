package org.vpilo.babymonitor.presentation.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.camera.presentation.CameraView
import org.vpilo.babymonitor.camera.presentation.CameraViewModel
import org.vpilo.babymonitor.model.di.AppRole
import org.vpilo.babymonitor.presentation.Theme

@Composable
fun ServerPreviewScreenRoot(
    modifier: Modifier = Modifier,
) {
    Column {
        Text(
            text = "Camera",
            style = MaterialTheme.typography.titleMedium,
            color = Theme.Colors.text,
        )
        Box(modifier = modifier.fillMaxSize()) {
            CameraView()
        }
    }
}
