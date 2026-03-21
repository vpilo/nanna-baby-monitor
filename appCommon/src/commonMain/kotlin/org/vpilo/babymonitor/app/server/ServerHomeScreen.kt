package org.vpilo.babymonitor.app.server

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.vpilo.babymonitor.camera.presentation.CameraViewFinder
import org.vpilo.babymonitor.presentation.Theme
import org.vpilo.babymonitor.presentation.composables.BackButton

@Composable
fun ServerHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ServerHomeViewModel,
    onBackClicked: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ServerHomeComposable(
        modifier = modifier,
        onBackClicked = onBackClicked,
        isServerAvailable = state.isAvailable,
    )
}

@Composable
private fun ServerHomeComposable(
    modifier: Modifier = Modifier,
    onBackClicked: () -> Unit,
    isServerAvailable: Boolean,
) {
    Column(modifier = modifier) {
        BackButton(
            modifier = Modifier,
            onBackClicked = onBackClicked,
        )

        Text(
            text = "Camera",
            style = MaterialTheme.typography.titleMedium,
            color = Theme.Colors.text,
        )
        Text(
            text = if (isServerAvailable) "Available for connections." else "Server not available!",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isServerAvailable) Theme.Colors.text else Theme.Colors.error,
        )
        Box(modifier = modifier.fillMaxSize()) {
            CameraViewFinder()
        }
    }
}
