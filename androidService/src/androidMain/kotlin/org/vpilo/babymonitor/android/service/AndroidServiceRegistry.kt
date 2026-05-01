package org.vpilo.babymonitor.android.service

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.common.Logger
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

object AndroidServiceRegistry : KoinComponent {
    private val services: MutableSet<WeakReference<AndroidService>> = mutableSetOf()

    private var serviceInstance: LifecycleService? = null

    private val scope = CoroutineScope(get<CoroutineContext>())

    val isServiceRunning: Boolean
        get() = serviceInstance != null

    fun register(service: AndroidService) {
        synchronized(services) {
            if (services.any { it.get() == service }) {
                Logger.w(TAG) { "Service $service is already registered" }
                return
            }

            services.add(WeakReference(service))
        }

        if (services.size == 1) {
            Logger.d(TAG) { "Requesting service start" }
            val context: Context = get()
            val intent = Intent(context, AndroidServiceHost::class.java)
            context.startForegroundService(intent)
        } else {
            serviceInstance?.let {
                scope.launch {
                    service.onServiceStarted(it, it)
                }
            }
        }
    }

    fun unregister(service: AndroidService) {
        if (services.none { it.get() == service }) {
            Logger.w(TAG) { "Service $service is not registered" }
            return
        }

        synchronized(services) {
            services.removeIf { with(it.get()) { this == service || this == null } }
        }

        if (services.isEmpty()) {
            Logger.d(TAG) { "Requesting service stop" }
            val context: Context = get()
            val intent = Intent(context, AndroidServiceHost::class.java)
            context.stopService(intent)
        } else {
            serviceInstance?.let {
                scope.launch {
                    service.onServiceStopped()
                }
            }
        }
    }

    internal fun reportServiceStarted(service: LifecycleService) {
        serviceInstance = service

        synchronized(services) {
            services.forEach { it.get()?.onServiceStarted(service, service) }
        }
    }

    internal fun reportServiceStopped() {
        serviceInstance = null

        synchronized(services) {
            services.forEach { it.get()?.onServiceStopped() }
        }
    }

    private val TAG = AndroidServiceRegistry::class
}
