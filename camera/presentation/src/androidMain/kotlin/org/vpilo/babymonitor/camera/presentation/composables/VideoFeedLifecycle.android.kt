package org.vpilo.babymonitor.camera.presentation.composables

import androidx.lifecycle.Lifecycle

/**
 * On Android the feed stops on PAUSE, when the Activity is put to the background.
 */
internal actual val minLifecycleStateForVideoFeed: Lifecycle.State =
    Lifecycle.State.RESUMED
