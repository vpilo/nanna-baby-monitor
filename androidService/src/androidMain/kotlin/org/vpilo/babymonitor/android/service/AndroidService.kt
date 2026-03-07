package org.vpilo.babymonitor.android.service

import android.content.Context
import androidx.lifecycle.LifecycleOwner

/**
 * Interface for services on Android which need to stay active when the app is not in the foreground.
 */
interface AndroidService {

    fun onServiceStarted(context: Context, lifecycleOwner: LifecycleOwner)

    fun onServiceStopped()
}
