package org.vpilo.babymonitor.settings.data

import java.io.File

internal expect fun getDataStoreFile(): File

internal const val DATA_STORE_FILE_NAME = "babymonitor.preferences_pb"
