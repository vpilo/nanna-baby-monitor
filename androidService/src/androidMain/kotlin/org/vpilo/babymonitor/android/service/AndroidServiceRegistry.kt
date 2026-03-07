package org.vpilo.babymonitor.android.service

import android.content.Context
import android.content.Intent
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.common.Logger
import java.lang.ref.WeakReference

object AndroidServiceRegistry : KoinComponent {
    private val services: MutableSet<WeakReference<AndroidService>> = mutableSetOf()

    private var isServiceRunning: Boolean = false

    fun register(service: AndroidService) {
        if (services.any { it.get() == service }) {
            Logger.w(TAG) { "Service $service is already registered" }
            return
        }

        services.add(WeakReference(service))

        if (services.size == 1) {
            Logger.d(TAG) { "Requesting service start" }
            val context: Context = get()
            val intent = Intent(context, AndroidServiceHost::class.java)
            context.startForegroundService(intent)
        }
    }

    fun unregister(service: AndroidService) {
        if (services.none { it.get() == service }) {
            Logger.w(TAG) { "Service $service is not registered" }
            return
        }

        services.removeIf { with(it.get()) { this == service || this == null } }

        if (services.isEmpty()) {
            Logger.d(TAG) { "Requesting service stop" }
            val context: Context = get()
            val intent = Intent(context, AndroidServiceHost::class.java)
            context.stopService(intent)
        }
    }

    fun isServiceRunning() = isServiceRunning

    internal fun reportServiceStarted(service: LifecycleService) {
        isServiceRunning = true
        services.forEach { it.get()?.onServiceStarted(service, service) }
    }

    internal fun reportServiceStopped() {
        isServiceRunning = false
        services.forEach { it.get()?.onServiceStopped() }
    }

    private val TAG = AndroidServiceRegistry::class
}
