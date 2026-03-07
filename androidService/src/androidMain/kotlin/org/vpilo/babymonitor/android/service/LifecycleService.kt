package org.vpilo.babymonitor.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.annotation.CallSuper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal abstract class LifecycleService : Service(), LifecycleOwner {
    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private val lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    private fun dispatch(event: Lifecycle.Event) {
        scope.launch {
            lifecycleRegistry.handleLifecycleEvent(event)
        }
    }

    @CallSuper
    override fun onBind(p0: Intent?): IBinder? {
        dispatch(Lifecycle.Event.ON_START)
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatch(Lifecycle.Event.ON_START)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        dispatch(Lifecycle.Event.ON_CREATE)
    }

    override fun onDestroy() {
        dispatch(Lifecycle.Event.ON_STOP)
        dispatch(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }
}
