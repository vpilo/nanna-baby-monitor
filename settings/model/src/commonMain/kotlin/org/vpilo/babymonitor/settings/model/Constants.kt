package org.vpilo.babymonitor.settings.model

import java.io.File

internal const val APP_DIR_NAME = "babymonitor"

internal val homeDir: File by lazy {
    checkNotNull(
        System
            .getProperty("user.home")
            ?.let(::File),
    ) { "Cannot detect user home directory location" }
}
