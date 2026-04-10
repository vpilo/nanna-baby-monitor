package org.vpilo.babymonitor.settings.model.ktx

import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.isSupportedOnCurrentPlatform

val Setting<*>.isSupportedOnCurrentPlatform: Boolean
    get() = platform.isSupportedOnCurrentPlatform
