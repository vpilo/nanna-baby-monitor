package org.vpilo.babymonitor.settings.data

import org.vpilo.babymonitor.settings.model.getSettingsDir
import java.io.File

internal fun getDataStoreFile(): File = File(getSettingsDir(), DATA_STORE_FILE_NAME)

internal const val DATA_STORE_FILE_NAME = "babymonitor.preferences_pb"
