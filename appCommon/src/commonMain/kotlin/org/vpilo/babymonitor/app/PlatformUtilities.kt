package org.vpilo.babymonitor.app

import androidx.compose.runtime.staticCompositionLocalOf

val LocalQuitApplication =
    staticCompositionLocalOf<() -> Unit> {
        error("LocalQuitApplication not provided")
    }
