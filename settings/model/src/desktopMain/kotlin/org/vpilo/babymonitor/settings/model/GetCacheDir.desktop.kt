package org.vpilo.babymonitor.settings.model

import java.io.File

actual fun getCacheDir(): File {
    val localAppData = System.getenv("LOCALAPPDATA")
    if (System.getProperty("os.name").startsWith("Windows") && !localAppData.isNullOrBlank()) {
        return File(File(localAppData, APP_DIR_NAME), "cache")
    }

    // Per the XDG base directory specification, a relative path is invalid and must be ignored.
    val cacheHome =
        System.getenv("XDG_CACHE_HOME")?.let(::File)?.takeIf { it.isAbsolute }
            ?: File(homeDir, ".cache")
    return File(cacheHome, APP_DIR_NAME)
}
