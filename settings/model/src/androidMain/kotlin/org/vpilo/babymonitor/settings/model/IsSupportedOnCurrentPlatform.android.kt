package org.vpilo.babymonitor.settings.model

actual val PlatformAvailability.isSupportedOnCurrentPlatform: Boolean
    get() =
        when (this) {
            PlatformAvailability.AllPlatforms,
            PlatformAvailability.AndroidOnly,
                -> true

            PlatformAvailability.DesktopOnly -> false
        }
