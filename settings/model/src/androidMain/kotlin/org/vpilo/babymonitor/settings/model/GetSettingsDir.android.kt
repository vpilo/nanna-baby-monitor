package org.vpilo.babymonitor.settings.model

import android.content.Context
import org.koin.mp.KoinPlatform
import java.io.File

actual fun getSettingsDir(): File {
    val context = KoinPlatform.getKoin().get<Context>()
    return context.filesDir
}
