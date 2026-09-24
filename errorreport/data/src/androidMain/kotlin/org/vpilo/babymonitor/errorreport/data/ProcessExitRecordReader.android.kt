package org.vpilo.babymonitor.errorreport.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import org.koin.mp.KoinPlatform

private const val MAX_EXIT_RECORDS = 16

internal actual fun readProcessExitRecords(): List<ProcessExitRecord> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()

    val context = KoinPlatform.getKoin().get<Context>()
    val activityManager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
    return activityManager
        .getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXIT_RECORDS)
        .map { it.toExitRecord() }
}

@RequiresApi(Build.VERSION_CODES.R)
private fun ApplicationExitInfo.toExitRecord(): ProcessExitRecord =
    ProcessExitRecord(
        pid = pid.toLong(),
        timestampMillis = timestamp,
        reason = EXIT_REASONS[reason] ?: ExitReason.UNKNOWN,
        reasonCode = reason,
        description = description,
        importance = importance,
        openTrace = { runCatching { traceInputStream }.getOrNull() },
    )

@RequiresApi(Build.VERSION_CODES.R)
private val EXIT_REASONS_API30 =
    mapOf(
        ApplicationExitInfo.REASON_CRASH to ExitReason.CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE to ExitReason.CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR to ExitReason.ANR,
        ApplicationExitInfo.REASON_LOW_MEMORY to ExitReason.LOW_MEMORY,
        ApplicationExitInfo.REASON_SIGNALED to ExitReason.SIGNALED,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE to ExitReason.EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_DEPENDENCY_DIED to ExitReason.DEPENDENCY_DIED,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to ExitReason.INITIALIZATION_FAILURE,
        ApplicationExitInfo.REASON_OTHER to ExitReason.OTHER,
        ApplicationExitInfo.REASON_UNKNOWN to ExitReason.UNKNOWN,
        ApplicationExitInfo.REASON_EXIT_SELF to ExitReason.EXIT_SELF,
        ApplicationExitInfo.REASON_USER_REQUESTED to ExitReason.USER_REQUESTED,
        ApplicationExitInfo.REASON_USER_STOPPED to ExitReason.USER_STOPPED,
        ApplicationExitInfo.REASON_PERMISSION_CHANGE to ExitReason.PERMISSION_CHANGE,
    )

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private val EXIT_REASONS_API33 =
    mapOf(
        ApplicationExitInfo.REASON_FREEZER to ExitReason.FREEZER,
    )

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private val EXIT_REASONS_API34 =
    mapOf(
        ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE to ExitReason.PACKAGE_STATE_CHANGE,
        ApplicationExitInfo.REASON_PACKAGE_UPDATED to ExitReason.PACKAGE_UPDATED,
    )

@RequiresApi(Build.VERSION_CODES.R)
private val EXIT_REASONS =
    when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> {
            EXIT_REASONS_API30
        }

        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
            EXIT_REASONS_API30 + EXIT_REASONS_API33
        }

        else -> {
            EXIT_REASONS_API30 + EXIT_REASONS_API33 + EXIT_REASONS_API34
        }
    }
