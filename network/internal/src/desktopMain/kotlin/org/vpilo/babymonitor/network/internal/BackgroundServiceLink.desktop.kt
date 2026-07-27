package org.vpilo.babymonitor.network.internal

import org.vpilo.babymonitor.model.AppRole

/**
 * Desktop [BackgroundServiceLink] to keep components alive in the background.
 * This does nothing, as on Desktop there are no restrictions to keep the app running in the background.
 */
actual class BackgroundServiceLink actual constructor(
    role: AppRole,
    onStoppedByPlatform: () -> Unit,
) {
    actual fun start() = Unit

    actual fun stop() = Unit
}
