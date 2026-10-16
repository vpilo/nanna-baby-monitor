package org.vpilo.babymonitor.network.internal.discovery

import android.net.wifi.WifiManager

internal class MulticastLock(
    private val wifiManager: WifiManager,
) {
    private var lock: WifiManager.MulticastLock? = null

    fun acquire(tag: String) {
        check(lock == null) { "Discovery lock already acquired" }

        val lock = wifiManager.createMulticastLock(tag)
        lock.setReferenceCounted(true)
        lock.acquire()
        this@MulticastLock.lock = lock
    }

    fun release() {
        val lock = checkNotNull(lock) { "Discovery lock not created" }
        check(lock.isHeld) { "Discovery lock not acquired" }

        lock.release()
        this@MulticastLock.lock = null
    }
}
