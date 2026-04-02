package org.vpilo.babymonitor.settings.data

import android.content.Context
import org.koin.mp.KoinPlatform
import java.io.File

internal actual fun getDataStoreFile(): File {
    val context = KoinPlatform.getKoin().get<Context>()
    return File(context.filesDir.absolutePath, DATA_STORE_FILE_NAME)
}
