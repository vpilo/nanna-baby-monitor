package org.vpilo.babymonitor.settings.data

import java.io.File

internal actual fun getDataStoreDir(): File {
    val home = System.getProperty("user.home")
    return File(home, ".config/babymonitor")
}
