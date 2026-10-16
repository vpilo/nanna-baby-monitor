package org.vpilo.babymonitor.settings.model

import java.io.File

fun getCacheStoreFile(): File = File(getCacheDir(), CACHE_STORE_FILE_NAME)

internal const val CACHE_STORE_FILE_NAME = "babymonitor.cache.preferences_pb"
