package org.vpilo.babymonitor.camera.presentation.composables

import androidx.lifecycle.Lifecycle

/**
 * Lowest lifecycle state where the video feed can be run.
 * Differs by platform due to non-matching lifecycle states.
 */
internal expect val minLifecycleStateForVideoFeed: Lifecycle.State
