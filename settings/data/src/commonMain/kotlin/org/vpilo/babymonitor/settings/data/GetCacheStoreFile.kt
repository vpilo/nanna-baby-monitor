package org.vpilo.babymonitor.settings.data

import org.vpilo.babymonitor.settings.model.getCacheDir
import java.io.File

internal fun getCacheStoreFile(): File = File(getCacheDir(), CACHE_STORE_FILE_NAME)

internal const val CACHE_STORE_FILE_NAME = "babymonitor.cache.preferences_pb"
