package org.vpilo.babymonitor.network.internal.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.toAttributes
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository
import kotlin.coroutines.CoroutineContext

internal actual class DefaultLocalDiscoveryRepository(
    context: Context,
    coroutineContext: CoroutineContext,
) : LocalDiscoveryRepository {
    actual constructor(coroutineContext: CoroutineContext) : this(
        context = KoinPlatform.getKoin().get(),
        coroutineContext = coroutineContext,
    )

    private var device: Device? = null

    private val applicationContext: Context = context.applicationContext

    private val nsdManager: NsdManager =
        applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val wifiManager: WifiManager =
        applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private var multicastLock: WifiManager.MulticastLock? = null

    private val mutableDiscoveredDevicesFlow: MutableStateFlow<Map<DeviceId, Device>> = MutableStateFlow(emptyMap())

    private val watchdog =
        DeviceWatchdog(
            devicesFlow = mutableDiscoveredDevicesFlow,
            onDeviceUnreachable = { removed ->
                Logger.i(TAG) { "Device unreachable: $removed" }
                mutableDiscoveredDevicesFlow.update { it - removed.id }
            },
            onDeviceReturned = { returned ->
                Logger.i(TAG) { "Device returned: $returned" }
                mutableDiscoveredDevicesFlow.update { it + (returned.id to returned) }
            },
            coroutineScope = CoroutineScope(coroutineContext),
        )

    private var discoveryListener: AndroidDiscoveryListener =
        AndroidDiscoveryListener(
            nsdManager = nsdManager,
            mutableDiscoveredDevicesFlow = mutableDiscoveredDevicesFlow,
        )
    private var registrationListener: AndroidRegistrationListener = AndroidRegistrationListener()

    actual override val discoveredDevicesFlow: Flow<Set<Device>> =
        @OptIn(FlowPreview::class)
        mutableDiscoveredDevicesFlow
            .debounce(LocalDiscoveryRepository.DISCOVERY_DEBOUNCE_TIME)
            .map { it.values.toSortedSet() }
            .distinctUntilChanged()

    private val mutableIsRegisteredFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    actual override val isRegisteredFlow: Flow<Boolean> = mutableIsRegisteredFlow.asStateFlow()

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
        mutableIsRegisteredFlow.value = true

        when (device) {
            is Device.Server -> {
                Logger.d(TAG) { "Registering service: $device" }
                nsdManager.registerService(createServiceInfo(device), NsdManager.PROTOCOL_DNS_SD, registrationListener)
            }

            is Device.Client,
            is Device.Relay,
                -> {
                    Logger.d(TAG) { "Starting service discovery" }
                    discoveryListener.reset(device)
                    nsdManager.discoverServices(
                        DISCOVERY_ANDROID_SERVICE_TYPE,
                        NsdManager.PROTOCOL_DNS_SD,
                        discoveryListener,
                    )
                    watchdog.startWatching()
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
        mutableIsRegisteredFlow.value = false
        mutableDiscoveredDevicesFlow.value = emptyMap()
        releaseMulticastLock()
        // It's apparently unreliable to keep using the same listener between sessions, so make a new one every time.
        discoveryListener =
            AndroidDiscoveryListener(
                nsdManager = nsdManager,
                mutableDiscoveredDevicesFlow = mutableDiscoveredDevicesFlow,
            )
        registrationListener = AndroidRegistrationListener()
        watchdog.stopWatching()
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
