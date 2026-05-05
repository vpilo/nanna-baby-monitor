package org.vpilo.babymonitor.android.service

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import org.vpilo.babymonitor.model.AppRole

/**
 * Interface for services on Android which need to stay active when the app is not in the foreground.
 */
interface AndroidService {
    val role: AppRole

    fun onServiceStarted(
        context: Context,
        lifecycleOwner: LifecycleOwner,
    )

    fun onServiceStopped()
}
