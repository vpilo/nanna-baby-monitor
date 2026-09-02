package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import org.koin.mp.KoinPlatform
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.network.internal.discovery.ktx.toAttributes
import org.vpilo.babymonitor.network.model.Constants
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryRepository
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.coroutines.CoroutineContext

internal actual class DefaultLocalDiscoveryRepository(
    private val coroutineContext: CoroutineContext,
) : LocalDiscoveryRepository {
    actual constructor() : this(
        coroutineContext = KoinPlatform.getKoin().get(),
    )

    private val discoveryService = JmDNS.create()

    private val listener = DesktopDiscoveryListener()

    private var device: Device? = null

    private val scope: CoroutineScope = CoroutineScope(coroutineContext)
    private var trimJob: Job? = null

    actual override val discoveredDevicesFlow: Flow<Set<Device>> =
        @OptIn(FlowPreview::class)
        listener.discoveredDevices
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
        trimJob?.cancel()
        trimJob =
            scope.launch {
                while (true) {
                    delay(Constants.DISCOVERY_TRIM_PERIOD)
                    listener.trim()
                }
            }
        listener.reset(device)
        mutableIsRegisteredFlow.value = true

        try {
            when (device) {
                is Device.LocalServer -> {
                    Logger.d(TAG) { "Registered service: $device" }
                    discoveryService.registerService(createServiceInfo(device))
                }

                is Device.Client,
                is Device.Relay,
                    -> {
                        Logger.d(TAG) { "Discovering services" }
                        discoveryService.addServiceListener(DISCOVERY_DESKTOP_SERVICE_TYPE, listener)
                    }

                else -> {
                    error("Invalid device type: $device")
                }
            }
        } catch (ex: IOException) {
            Logger.e(TAG, ex) { "Failed to register server service" }
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
            is Device.LocalServer -> {
                Logger.d(TAG) { "Unregistered service" }
                discoveryService.unregisterAllServices()
            }

            is Device.Client -> {
                Logger.d(TAG) { "Discovery stopped" }
                discoveryService.removeServiceListener(DISCOVERY_DESKTOP_SERVICE_TYPE, listener)
            }

            else -> {
                error("Invalid device type: $device")
            }
        }
        this.device = null
        listener.reset()
        mutableIsRegisteredFlow.value = false
        trimJob?.cancel()
        trimJob = null
    }

    actual override fun refresh() {
        val currentDevice = device ?: return
        unregister()
        register(currentDevice)
    }

    companion object {
        val TAG = DefaultLocalDiscoveryRepository::class

        // JmDNS requires the ".local." suffix
        private const val DISCOVERY_DESKTOP_SERVICE_TYPE = "_${Constants.DISCOVERY_SERVICE_TYPE}._tcp.local."

        private fun createServiceInfo(device: Device): ServiceInfo =
            ServiceInfo.create(
                DISCOVERY_DESKTOP_SERVICE_TYPE,
                device.idString,
                Constants.SERVICE_PORT,
                0,
                0,
                device.toAttributes(),
            )
    }
}
