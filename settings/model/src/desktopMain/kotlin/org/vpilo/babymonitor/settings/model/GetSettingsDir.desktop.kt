package org.vpilo.babymonitor.settings.model

import java.io.File

actual fun getSettingsDir(): File {
    val home = System.getProperty("user.home")
    return File(home, ".config/babymonitor")
}
