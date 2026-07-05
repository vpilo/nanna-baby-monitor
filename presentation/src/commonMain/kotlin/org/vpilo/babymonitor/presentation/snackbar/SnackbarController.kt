package org.vpilo.babymonitor.presentation.snackbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.vpilo.babymonitor.common.Logger

class SnackbarController internal constructor() {
    private val snackbarMessages = MutableSharedFlow<String>(extraBufferCapacity = 2)

    fun show(message: String) {
        if (!snackbarMessages.tryEmit(message)) {
            Logger.d(TAG) { "Dropped message, queue full: '${message.take(25)}'" }
        }
    }

    internal suspend fun awaitSnacks(snackbarHostState: SnackbarHostState) {
        snackbarMessages.asSharedFlow().collect { message ->
            snackbarHostState.showSnackbar(message = message, duration = SnackbarDuration.Short)
        }
    }

    private companion object {
        private val TAG = SnackbarController::class
    }
}
