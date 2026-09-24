package org.vpilo.babymonitor.errorreport.data

import android.os.Build
import android.os.Process

internal actual fun retrievePlatformMetadata(): Map<String, String> =
    mapOf(
        "manufacturer" to Build.MANUFACTURER,
        "model" to Build.MODEL,
        "osVersion" to Build.VERSION.RELEASE,
        "sdk" to Build.VERSION.SDK_INT.toString(),
    )

internal actual fun currentProcessId(): Long = Process.myPid().toLong()
