package org.vpilo.babymonitor.android.service

import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AppRole
import java.lang.ref.WeakReference
import kotlin.coroutines.CoroutineContext

object AndroidServiceRegistry : KoinComponent {
    private val services: MutableSet<WeakReference<AndroidService>> = mutableSetOf()

    private var serviceInstance: LifecycleService? = null

    private val scope = CoroutineScope(get<CoroutineContext>())

    val currentRole: AppRole
        get() =
            synchronized(services) {
                services.firstNotNullOfOrNull { it.get()?.role } ?: AppRole.UNDECIDED
            }

    fun register(service: AndroidService) {
        synchronized(services) {
            if (services.any { it.get() == service }) {
                Logger.w(TAG) { "Service $service is already registered" }
                return
            }

            val existingRole = services.firstNotNullOfOrNull { it.get()?.role }
            check(existingRole == null || existingRole == service.role) {
                "Cannot register service with role ${service.role}; existing services use $existingRole"
            }

            services.add(WeakReference(service))
        }

        if (services.size == 1) {
            Logger.d(TAG) { "Requesting service start" }
            val context: Context = get()
            val intent = Intent(context, AndroidServiceHost::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
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
