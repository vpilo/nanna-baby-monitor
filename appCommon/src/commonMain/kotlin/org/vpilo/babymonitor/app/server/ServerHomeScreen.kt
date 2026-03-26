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

@Composable
fun ServerHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: ServerHomeScreenViewModel,
) {
    val state by viewModel.stateFlow.collectAsStateWithLifecycle()

    ServerHomeContent(
        modifier = modifier,
        isServerAvailable = state.isAvailable,
    )
}

@Composable
private fun ServerHomeContent(
    modifier: Modifier,
    isServerAvailable: Boolean,
) {
    Column(modifier = modifier) {
        Text(
            text = if (isServerAvailable) "Available for connections." else "Server not available!",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isServerAvailable) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.error,
        )
        Box(modifier = modifier.fillMaxSize()) {
            CameraViewFinder()
        }
    }
}
