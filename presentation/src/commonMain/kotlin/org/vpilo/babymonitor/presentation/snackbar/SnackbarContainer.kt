package org.vpilo.babymonitor.presentation.snackbar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SnackbarContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val controller = remember { SnackbarController() }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = modifier.fillMaxSize()) {
        SnackbarHost(hostState = snackbarHostState, Modifier.align(Alignment.BottomCenter))

        LaunchedEffect(scope) {
            controller.awaitSnacks(snackbarHostState)
        }

        CompositionLocalProvider(LocalSnackbarController provides controller) {
            content()
        }
    }
}
