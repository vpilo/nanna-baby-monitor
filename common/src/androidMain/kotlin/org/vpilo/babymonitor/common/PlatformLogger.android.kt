package org.vpilo.babymonitor.common

import android.util.Log

actual val platformLogger: PlatformLogger =
    PlatformLogger { tag, level, message, throwable ->
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARN -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
