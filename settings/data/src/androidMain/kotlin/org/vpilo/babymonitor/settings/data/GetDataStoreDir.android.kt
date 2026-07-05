package org.vpilo.babymonitor.settings.data

import android.content.Context
import org.koin.mp.KoinPlatform
import java.io.File

internal actual fun getDataStoreDir(): File {
    val context = KoinPlatform.getKoin().get<Context>()
    return context.filesDir
}
