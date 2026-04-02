package org.vpilo.babymonitor.settings.data

import java.io.File

internal actual fun getDataStoreFile(): File {
    val home = System.getProperty("user.home")
    val appDir = File(home, ".config/babymonitor")
    return File(appDir, DATA_STORE_FILE_NAME)
}
