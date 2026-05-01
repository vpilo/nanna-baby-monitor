package org.vpilo.babymonitor.network.common

import org.vpilo.babymonitor.model.AppRole

/**
 * Desktop [ForegroundServiceLink] to keep components alive in the background.
 *
 * This does nothing, as on Desktop there are no restrictions to keep the app running in the background.
 */
actual class ForegroundServiceLink actual constructor(
    role: AppRole,
) {
    actual fun start() = Unit

    actual fun stop() = Unit
}
