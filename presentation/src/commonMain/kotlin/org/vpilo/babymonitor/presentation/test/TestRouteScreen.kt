package org.vpilo.babymonitor.presentation.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import org.vpilo.babymonitor.camera.presentation.CameraView

@Composable
fun TestRouteScreenRoot(
    modifier: Modifier = Modifier,
    viewModel: TestRouteViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CameraView()

}
