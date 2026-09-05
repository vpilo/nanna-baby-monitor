package org.vpilo.babymonitor.network.internal.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.internal.discovery.ktx.isReachable
import kotlin.time.Duration.Companion.seconds

/**
 * Periodically probes discovered devices in [devicesFlow] and removes unreachable ones.
 *
 * Local discovery protocols do not detect when a device has gone away from the current local network, so we need to test them periodically
 * to know they're still reachable.
 * Leaves some tolerance for failures before considering a device as lost.
 *
 * Removed devices keep being probed for a while: discovery only announces a device again once its records change, so one that comes back
 * on the same address would stay invisible otherwise.
 */
internal class DeviceWatchdog(
    private val devicesFlow: StateFlow<Map<DeviceId, Device>>,
    private val onDeviceUnreachable: (Device) -> Unit,
    private val onDeviceReturned: (Device) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    private val failedProbes: MutableMap<DeviceId, Int> = mutableMapOf()

    private val unreachableDevices: MutableMap<DeviceId, Device> = mutableMapOf()

    private var probingJob: Job? = null

    fun startWatching() {
        probingJob?.cancel()
        probingJob =
            coroutineScope.launch {
                watch()
            }
    }

    fun stopWatching() {
        probingJob?.cancel()
        probingJob = null
    }

    private suspend fun watch() {
        coroutineScope {
            failedProbes.clear()
            unreachableDevices.clear()
            while (isActive) {
                delay(PROBE_INTERVAL)
                // Devices announced again by discovery are back on their own.
                unreachableDevices.keys.removeAll(devicesFlow.value.keys)

                (devicesFlow.value.values + unreachableDevices.values)
                    .map { device -> async { device to device.isReachable() } }
                    .awaitAll()
                    .forEach { (device, isReachable) ->
                        if (isReachable) {
                            onProbeSucceeded(device)
                        } else {
                            onProbeFailed(device)
                        }
                    }
                failedProbes.keys.retainAll(devicesFlow.value.keys + unreachableDevices.keys)
            }
        }
    }

    private fun onProbeSucceeded(device: Device) {
        failedProbes -= device.id
        unreachableDevices
            .remove(device.id)
            ?.let { onDeviceReturned(device) }
    }

    private fun onProbeFailed(device: Device) {
        val failures = (failedProbes[device.id] ?: 0) + 1
        failedProbes[device.id] = failures

        when {
            failures == MAX_FAILED_PROBES -> {
                unreachableDevices[device.id] = device
                onDeviceUnreachable(device)
            }

            // The device stayed away long enough: stop probing it, only a new announcement can bring it back now.
            failures >= MAX_FAILED_PROBES_WHILE_UNREACHABLE -> {
                failedProbes -= device.id
                unreachableDevices -= device.id
            }
        }
    }

    private companion object {
        private const val MAX_FAILED_PROBES = 3

        // Around five minutes at PROBE_INTERVAL.
        private const val MAX_FAILED_PROBES_WHILE_UNREACHABLE = 30

        private val PROBE_INTERVAL = 10.seconds
    }
}
