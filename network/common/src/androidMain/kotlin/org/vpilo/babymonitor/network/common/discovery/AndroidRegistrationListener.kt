package org.vpilo.babymonitor.network.common.discovery

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import org.vpilo.babymonitor.common.Logger

class AndroidRegistrationListener : NsdManager.RegistrationListener {
    override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
        Logger.d(DiscoveryManager.TAG) { "Service registered" }
    }

    override fun onRegistrationFailed(
        serviceInfo: NsdServiceInfo,
        errorCode: Int,
    ) {
        Logger.e(DiscoveryManager.TAG) { "Service registration failed: errorCode=$errorCode" }
    }

    override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
        Logger.d(DiscoveryManager.TAG) { "Service unregistered" }
    }

    override fun onUnregistrationFailed(
        serviceInfo: NsdServiceInfo,
        errorCode: Int,
    ) {
        Logger.e(DiscoveryManager.TAG) { "Service unregistration failed: errorCode=$errorCode" }
    }
}
