package org.vpilo.babymonitor.presentation.snackbar

import androidx.compose.runtime.staticCompositionLocalOf

val LocalSnackbarController =
    staticCompositionLocalOf<SnackbarController> {
        error("LocalSnackbarController not provided")
    }
