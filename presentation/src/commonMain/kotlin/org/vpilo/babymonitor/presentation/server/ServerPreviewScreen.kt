package org.vpilo.babymonitor.presentation.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
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

@Composable
fun ServerPreviewScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: ServerPreviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val isRecording = remember { mutableStateOf(false) }

    Column {
        Spacer(modifier = Modifier.height(50.dp))
        Button(
            {
                isRecording.value = !isRecording.value
            },
        ) {
            if (isRecording.value) {
                Text("Stop")
            } else {
                Text("Start")
            }
        }
        if (isRecording.value) {
            Text("Recording")
            Box(modifier = modifier.fillMaxSize()) {
                CameraView()
            }
        } else {
            Text("Ready")
        }
    }
}
