package org.vpilo.babymonitor.network.common.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.LocalDiscoveryRepository
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.discovery.ktx.toAttributes

internal actual class DefaultLocalDiscoveryRepository(
    context: Context,
) : LocalDiscoveryRepository {
    actual constructor() : this(
        context = KoinPlatform.getKoin().get(),
    )

    private var device: Device? = null

    private val applicationContext: Context = context.applicationContext

    private val nsdManager: NsdManager =
        applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val wifiManager: WifiManager =
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null

    private var discoveryListener: AndroidDiscoveryListener = AndroidDiscoveryListener(nsdManager = nsdManager)
    private var registrationListener: AndroidRegistrationListener = AndroidRegistrationListener()

    actual override val discoveredDevicesFlow: Flow<Set<Device>>
        get() =
            discoveryListener.discoveredDevices
                .map { it.toSortedSet() }
                .distinctUntilChanged()

    actual override fun register(device: Device) {
        if (this.device == device) {
            return
        }
        if (this.device != null) {
            Logger.i(TAG) { "Service was already registered for ${this.device}! Changing to $device" }
            unregister()
        }
        this.device = device
        acquireMulticastLock()
        when (device) {
            is Device.Server -> {
                Logger.d(TAG) { "Registering service: $device" }
                nsdManager.registerService(createServiceInfo(device), NsdManager.PROTOCOL_DNS_SD, registrationListener)
            }

            is Device.Client -> {
                Logger.d(TAG) { "Starting service discovery" }
                discoveryListener.setDevice(device)
                nsdManager.discoverServices(
                    DISCOVERY_ANDROID_SERVICE_TYPE,
                    NsdManager.PROTOCOL_DNS_SD,
                    discoveryListener,
                )
            }
        }
    }

    actual override fun unregister() {
        val device =
            if (this.device == null) {
                Logger.d(TAG) { "Service was not registered" }
                return
            } else {
                this.device
            }

        when (device) {
            is Device.Server -> {
                Logger.d(TAG) { "Unregistering service" }
                nsdManager.unregisterService(registrationListener)
            }

            is Device.Client -> {
                Logger.d(TAG) { "Stopping discovery" }
                try {
                    nsdManager.stopServiceDiscovery(discoveryListener)
                } catch (ex: IllegalArgumentException) {
                    Logger.e(TAG, ex) { "Failed to stop discovery" }
                }
            }

            else -> {
                error("Invalid device type: $device")
            }
        }
        this.device = null
        discoveryListener.reset()
        releaseMulticastLock()
        // It's apparently unreliable to keep using the same listener between sessions, so make a new one every time.
        discoveryListener = AndroidDiscoveryListener(nsdManager = nsdManager)
        registrationListener = AndroidRegistrationListener()
    }

    actual override fun refresh() {
        val currentDevice = device ?: return
        unregister()
        register(currentDevice)
    }

    private fun acquireMulticastLock() {
        check(multicastLock == null) { "Discovery lock already acquired" }
        val device = checkNotNull(device) { "Device not registered" }

        val lock = wifiManager.createMulticastLock(device.idString)
        lock.setReferenceCounted(true)
        lock.acquire()
        multicastLock = lock
        Logger.d(TAG) { "Multicast lock acquired" }
    }

    private fun releaseMulticastLock() {
        val lock = checkNotNull(multicastLock) { "Discovery lock not created" }
        check(lock.isHeld) { "Discovery lock not acquired" }

        lock.release()
        Logger.d(TAG) { "Multicast lock released" }
        multicastLock = null
    }

    companion object {
        val TAG = DefaultLocalDiscoveryRepository::class

        private fun createServiceInfo(device: Device): NsdServiceInfo =
            NsdServiceInfo().apply {
                serviceName = device.idString
                serviceType = DISCOVERY_ANDROID_SERVICE_TYPE
                port = Constants.SERVICE_PORT
                device.toAttributes().forEach { (name, value) ->
                    setAttribute(name, value)
                }
            }

        // Android NSD requires a "_tcp." suffix.
        private const val DISCOVERY_ANDROID_SERVICE_TYPE = "_${Constants.DISCOVERY_SERVICE_TYPE}._tcp."
    }
}
