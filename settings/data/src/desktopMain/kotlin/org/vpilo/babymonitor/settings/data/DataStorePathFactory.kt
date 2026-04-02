package org.vpilo.babymonitor.settings.data

import okio.Path
import okio.Path.Companion.toPath

actual class DataStorePathFactory {
    actual fun createPath(): Path {
        val home = System.getProperty("user.home")
        return "$home/.config/babymonitor.preferences_pb".toPath()
    }
}
