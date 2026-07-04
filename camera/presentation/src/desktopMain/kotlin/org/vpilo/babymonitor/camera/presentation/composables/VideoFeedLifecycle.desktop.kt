package org.vpilo.babymonitor.camera.presentation.composables

import androidx.lifecycle.Lifecycle

/**
 * On Desktop the feed stops on STOP, when the window is minimized.
 * PAUSE cannot be used because it is set on focus loss.
 */
internal actual val minLifecycleStateForVideoFeed: Lifecycle.State = Lifecycle.State.STARTED
