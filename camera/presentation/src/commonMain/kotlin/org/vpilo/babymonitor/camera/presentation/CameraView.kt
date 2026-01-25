package org.vpilo.babymonitor.camera.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = koinViewModel()
) {
//    val cam = makeCamera(LocalLifecycleOwner.current)
//    println("starting with inst ${cam.hashCode()}")

//    val permissionManager = rememberCameraPermissionManager()
    var isRecording = remember { mutableStateOf(false) }
    var currentState = remember { mutableStateOf("loading") }
    LaunchedEffect(Unit) {
//        permissionManager.requestCameraPermissions()
        CoroutineScope(Dispatchers.Default).launch {
            /*
            cam.initialize()
            val recording = cam.startRecording()
isRecording.value = true
            currentState.value = "started"
            repeat(4) {
                delay(3.seconds)
            currentState.value = "progress ${recording.isRecording}"

            }
            val result = recording.stop()
            when (result){
                is VideoRecordingResult.Success ->
            currentState.value = "stopped: length ${result.durationMs} at ${result.uri}"
                    else ->
            currentState.value = "failed: ${result}"
            }

             */
        }
    }

    Column {
        Spacer(modifier = Modifier.height(50.dp))
        Button(
            {
                isRecording.value = !isRecording.value
                viewModel.setEnabled(isRecording.value)
            },
        ) {
            if (isRecording.value) {
                Text("Stop")
            } else {
                Text("Start")
            }
        }
        if (isRecording.value) {
            Text("Recording: ${currentState.value}")
//            CameraPreview(
//                modifier = modifier.fillMaxSize(),
//                onCameraControllerReady = { controller ->
//                    println("camera preview ready with inst ${controller.hashCode()}")
//                },
//            )
        } else {
            Text("waiting")
        }
    }
}
