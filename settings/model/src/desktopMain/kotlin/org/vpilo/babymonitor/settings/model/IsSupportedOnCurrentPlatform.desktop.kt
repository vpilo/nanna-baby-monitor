package org.vpilo.babymonitor.settings.model

actual val PlatformAvailability.isSupportedOnCurrentPlatform: Boolean
    get() =
        when (this) {
            PlatformAvailability.AllPlatforms,
            PlatformAvailability.DesktopOnly,
                -> true

            PlatformAvailability.AndroidOnly -> false
        }
