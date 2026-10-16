package org.vpilo.babymonitor.network.model.usecase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.network.model.pairing.PairedDevice
import org.vpilo.babymonitor.network.model.repository.DiscoveredDeviceCacheRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryClientRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.model.repository.RemoteDiscoveryRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class GetConnectableServersFlowUseCaseTest {
    @Test
    fun excludesVisibleServersWithoutPairing() =
        runTest {
            val fixture = Fixture()
            fixture.cache.set(setOf(localCamera("127.0.0.1")))
            fixture.collectIn(this) { results ->
                assertEquals(emptySet(), results.next())

                fixture.pairing.pair(camera)
                assertEquals(setOf(localCamera("127.0.0.1")), results.next())
            }
        }

    @Test
    fun cacheChangesAddAndRemoveConnectableServers() =
        runTest {
            val fixture = Fixture(camera)
            fixture.cache.set(setOf(localCamera("127.0.0.1")))
            fixture.collectIn(this) { results ->
                assertEquals(setOf(localCamera("127.0.0.1")), results.next())

                fixture.cache.set(emptySet())
                assertEquals(emptySet(), results.next())

                fixture.cache.set(setOf(localCamera("127.0.0.1")))
                assertEquals(setOf(localCamera("127.0.0.1")), results.next())
            }
        }

    @Test
    fun addsServersWithoutMutatingPreviousResults() =
        runTest {
            val fixture = Fixture(camera, otherCamera)
            fixture.cache.set(setOf(localCamera("127.0.0.1")))
            fixture.collectIn(this) { results ->
                val firstResult = results.next()
                assertEquals(setOf(localCamera("127.0.0.1")), firstResult)

                fixture.cache.set(setOf(localCamera("127.0.0.1"), localOtherCamera))
                assertEquals(setOf(localCamera("127.0.0.1"), localOtherCamera), results.next())
                assertEquals(setOf(localCamera("127.0.0.1")), firstResult)
            }
        }

    @Test
    fun changedCacheReplacesTheAddress() =
        runTest {
            val fixture = Fixture(camera)
            fixture.cache.set(setOf(localCamera("127.0.0.1")))
            fixture.collectIn(this) { results ->
                assertEquals(setOf(localCamera("127.0.0.1")), results.next())

                fixture.cache.set(setOf(localCamera("127.0.0.2")))
                assertEquals(setOf(localCamera("127.0.0.2")), results.next())
            }
        }

    @Test
    fun localDiscoveryTakesPrecedenceOverCachedAndRemoteServers() =
        runTest {
            val fixture = Fixture(camera)
            fixture.cache.set(setOf(localCamera("127.0.0.1")))
            fixture.remote.discoveredDevicesFlow.value = setOf(remoteCamera)
            fixture.collectIn(this) { results ->
                assertEquals(setOf(localCamera("127.0.0.1")), results.next())

                fixture.local.discoveredDevicesFlow.value = setOf(localCamera("127.0.0.2"))
                assertEquals(setOf(localCamera("127.0.0.2")), results.next())

                fixture.local.discoveredDevicesFlow.value = emptySet()
                assertEquals(setOf(localCamera("127.0.0.1")), results.next())

                fixture.cache.set(emptySet())
                assertEquals(setOf(remoteCamera), results.next())
            }
        }

    @Test
    fun discoversPairedServersThroughLocalAndRemoteDiscovery() =
        runTest {
            val fixture = Fixture(camera, otherCamera)
            fixture.collectIn(this) { results ->
                assertEquals(emptySet(), results.next())

                fixture.local.discoveredDevicesFlow.value = setOf(localOtherCamera)
                assertEquals(setOf(localOtherCamera), results.next())

                fixture.remote.discoveredDevicesFlow.value = setOf(remoteCamera)
                assertEquals(setOf(localOtherCamera, remoteCamera), results.next())
            }
        }

    @Test
    fun unpairingRemovesTheServerEvenWhileItRemainsVisible() =
        runTest {
            val fixture = Fixture(camera)
            fixture.cache.set(setOf(localCamera("127.0.0.1")))
            fixture.collectIn(this) { results ->
                assertEquals(setOf(localCamera("127.0.0.1")), results.next())

                fixture.pairing.unpair(cameraId)
                assertEquals(emptySet(), results.next())
            }
        }

    @Test
    fun cancellingCollectionStopsWatchingDiscoveryAndPairing() =
        runTest {
            val fixture = Fixture(camera)
            fixture.collectIn(this) { results ->
                assertEquals(emptySet(), results.next())
                assertEquals(1, fixture.cache.cachedDevicesFlow.subscriptionCount.value)
                assertEquals(1, fixture.local.discoveredDevicesFlow.subscriptionCount.value)
                assertEquals(1, fixture.remote.discoveredDevicesFlow.subscriptionCount.value)
                assertEquals(1, fixture.pairing.pairedDevices.subscriptionCount.value)
            }
            assertEquals(0, fixture.cache.cachedDevicesFlow.subscriptionCount.value)
            assertEquals(0, fixture.local.discoveredDevicesFlow.subscriptionCount.value)
            assertEquals(0, fixture.remote.discoveredDevicesFlow.subscriptionCount.value)
            assertEquals(0, fixture.pairing.pairedDevices.subscriptionCount.value)
        }

    private class Fixture(
        vararg paired: PairedDevice,
    ) {
        val pairing = FakePairingStorageRepository(paired.toList())
        val cache = FakeDiscoveredDeviceCacheRepository()
        val local = FakeLocalDiscoveryClientRepository()
        val remote = FakeRemoteDiscoveryRepository()
        private val useCase =
            GetConnectableServersFlowUseCase(
                GetPairedServersFlowUseCase(pairing),
                GetVisibleServersFlowUseCase(local, cache, remote),
            )

        suspend fun collectIn(
            scope: CoroutineScope,
            block: suspend (Channel<Set<Device.Server>>) -> Unit,
        ) {
            val results = Channel<Set<Device.Server>>(Channel.UNLIMITED)
            val job =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    useCase().collect { results.send(it) }
                }
            try {
                block(results)
            } finally {
                job.cancelAndJoin()
                results.close()
            }
        }
    }

    private class FakeDiscoveredDeviceCacheRepository : DiscoveredDeviceCacheRepository {
        override val cachedDevicesFlow = MutableStateFlow<Set<Device>>(emptySet())

        override suspend fun set(devices: Set<Device>) {
            cachedDevicesFlow.value = devices
        }
    }

    private class FakeLocalDiscoveryClientRepository : LocalDiscoveryClientRepository {
        override val discoveredDevicesFlow = MutableStateFlow<Set<Device>>(emptySet())
        override val isRegisteredFlow = flowOf(false)

        override fun startDiscovery(device: Device) = Unit

        override fun stopDiscovery() = Unit

        override fun refresh() = Unit
    }

    private class FakeRemoteDiscoveryRepository : RemoteDiscoveryRepository {
        override val discoveredDevicesFlow = MutableStateFlow<Set<Device>>(emptySet())
        override val isRegisteredFlow = flowOf(false)
        override val relayVersionMismatchFlow = flowOf(null)

        override suspend fun start() = Unit

        override suspend fun stop() = Unit
    }

    private class FakePairingStorageRepository(
        paired: List<PairedDevice>,
    ) : PairingStorageRepository {
        override val pairedDevices = MutableStateFlow(paired)

        override suspend fun pair(device: PairedDevice) {
            pairedDevices.value = pairedDevices.value.filterNot { it.deviceId == device.deviceId } + device
        }

        override suspend fun find(deviceId: DeviceId): PairedDevice? =
            pairedDevices.value.firstOrNull { it.deviceId == deviceId.toString() }

        override suspend fun unpair(deviceId: DeviceId) {
            pairedDevices.value = pairedDevices.value.filterNot { it.deviceId == deviceId.toString() }
        }

        override suspend fun updateName(
            deviceId: DeviceId,
            newName: String,
        ) {
            pairedDevices.value =
                pairedDevices.value.map {
                    if (it.deviceId == deviceId.toString()) it.copy(name = newName) else it
                }
        }
    }

    private companion object {
        private val cameraId = DeviceId.parse("00000000-0000-0000-0000-000000000001")
        private val camera = PairedDevice(cameraId.toString(), "Camera", "fingerprint", "secret")
        private val otherCameraId = DeviceId.parse("00000000-0000-0000-0000-000000000002")
        private val otherCamera = PairedDevice(otherCameraId.toString(), "Other camera", "fingerprint", "secret")
        private val localOtherCamera = Device.LocalServer(otherCameraId, "Other camera", NetworkAddress("127.0.0.1"))
        private val remoteCamera = Device.RemoteServer(cameraId, "Camera", NetworkAddress("relay.example"))

        private fun localCamera(address: String): Device.Server = Device.LocalServer(cameraId, "Camera", NetworkAddress(address))

        private suspend fun Channel<Set<Device.Server>>.next(): Set<Device.Server> = withTimeout(10_000) { receive() }
    }
}
