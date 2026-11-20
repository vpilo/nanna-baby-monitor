package org.vpilo.babymonitor.app.cameraselection

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.vpilo.babymonitor.app.settings.ClientLastServerId
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.NetworkAddress
import org.vpilo.babymonitor.model.repository.ConnectionState
import org.vpilo.babymonitor.model.repository.ConnectionState.ErrorReason
import org.vpilo.babymonitor.model.repository.DeviceId
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.LocalClientDeviceRepository
import org.vpilo.babymonitor.network.model.ServerState
import org.vpilo.babymonitor.network.model.pairing.PairedDevice
import org.vpilo.babymonitor.network.model.repository.DiscoveredDeviceCacheRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryClientRepository
import org.vpilo.babymonitor.network.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.network.model.repository.PairingStorageRepository
import org.vpilo.babymonitor.network.model.repository.RemoteDiscoveryRepository
import org.vpilo.babymonitor.network.model.usecase.GetConnectableServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetNewServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedNonVisibleServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetPairedServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.GetVisibleServersFlowUseCase
import org.vpilo.babymonitor.network.model.usecase.UpdateServerNamesUseCase
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CameraSelectionScreenViewModelTest {
    @Test
    fun reconnectsOnlyWhenTheLastCameraIsRediscoveredAfterRetriesRunOut() =
        withPicker(ErrorReason.ServerNotFound) { fixture ->
            runCurrent()
            assertTrue(fixture.requests.isEmpty())

            fixture.discovered.value = setOf(otherCamera, returnedCamera)
            runCurrent()
            assertEquals(listOf(returnedCamera), fixture.requests)

            fixture.connectionState.value = ConnectionState.Connecting(returnedCamera)
            runCurrent()
            fixture.connectionState.value = ConnectionState.Disconnected(ErrorReason.ServerNotFound)
            runCurrent()
            assertEquals(listOf(returnedCamera), fixture.requests)

            fixture.discovered.value = setOf(otherCamera)
            runCurrent()
            fixture.discovered.value = setOf(otherCamera, returnedCamera)
            runCurrent()
            assertEquals(listOf(returnedCamera, returnedCamera), fixture.requests)
        }

    @Test
    fun usesTheCacheAtStartupAndStopsAfterAnExplicitDisconnect() =
        withPicker(ErrorReason.NotConnectedYet) { fixture ->
            runCurrent()
            assertEquals(listOf(cachedCamera), fixture.requests)

            fixture.connectionState.value = ConnectionState.Disconnected(ErrorReason.ClientQuit)
            runCurrent()
            fixture.discovered.value = setOf(returnedCamera)
            runCurrent()
            assertEquals(listOf(cachedCamera), fixture.requests)
        }

    private fun withPicker(
        initialReason: ErrorReason,
        test: suspend TestScope.(Fixture) -> Unit,
    ) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(initialReason)
        val picker = fixture.createViewModel()
        try {
            backgroundScope.launch { picker.stateFlow.collect {} }
            backgroundScope.launch {
                picker.effectsFlow.filterIsInstance<CameraSelectionScreenEffect.ConnectToServer>().collect {
                    fixture.requests.add(it.server)
                }
            }
            test(fixture)
        } finally {
            picker.viewModelScope.cancel()
            runCurrent()
            Dispatchers.resetMain()
        }
    }

    private class Fixture(
        initialReason: ErrorReason,
    ) {
        val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected(initialReason))
        val discovered = MutableStateFlow<Set<Device>>(setOf(otherCamera))
        val requests = mutableListOf<Device.Server>()

        fun createViewModel(): CameraSelectionScreenViewModel {
            val local =
                object : LocalDiscoveryClientRepository {
                    override val discoveredDevicesFlow = discovered
                    override val isRegisteredFlow = flowOf(true)

                    override fun startDiscovery(device: Device) = Unit

                    override fun stopDiscovery() = Unit

                    override fun refresh() = Unit
                }
            val remote =
                object : RemoteDiscoveryRepository {
                    override val discoveredDevicesFlow = flowOf(emptySet<Device>())
                    override val isRegisteredFlow = flowOf(false)
                    override val relayVersionMismatchFlow = emptyFlow<Nothing>()

                    override suspend fun start() = Unit

                    override suspend fun stop() = Unit
                }
            val cache =
                object : DiscoveredDeviceCacheRepository {
                    override val cachedDevicesFlow = flowOf(setOf(cachedCamera))

                    override suspend fun set(devices: Set<Device>) = error("Unexpected cache write")
                }
            val pairing =
                object : PairingStorageRepository {
                    private val cameras =
                        listOf(cachedCamera, otherCamera).associate { camera ->
                            camera.id to PairedDevice(camera.idString, camera.name, "fingerprint", "secret")
                        }
                    override val pairedDevices = flowOf(cameras.values.toList())

                    override suspend fun find(deviceId: DeviceId) = cameras[deviceId]

                    override suspend fun pair(device: PairedDevice) = error("Unexpected pairing")

                    override suspend fun unpair(deviceId: DeviceId) = error("Unexpected unpairing")

                    override suspend fun updateName(
                        deviceId: DeviceId,
                        newName: String,
                    ) = Unit
                }
            val visible = GetVisibleServersFlowUseCase(local, cache, remote)
            val paired = GetPairedServersFlowUseCase(pairing)
            return CameraSelectionScreenViewModel(
                networkClientRepository =
                    object : NetworkClientRepository {
                        override val connectionStateFlow = connectionState
                        override val serverStateFlow = emptyFlow<ServerState>()

                        override suspend fun connect(server: Device.Server) = error("The screen handles connection effects")

                        override suspend fun disconnect() = error("Unexpected disconnect")

                        override suspend fun reset() = error("Unexpected reset")
                    },
                settingsRepository = LastCameraSettingsRepository(),
                getPairedNonVisibleServersFlowUseCase = GetPairedNonVisibleServersFlowUseCase(paired, visible),
                getConnectableServersFlowUseCase = GetConnectableServersFlowUseCase(paired, visible),
                getVisibleServersFlowUseCase = visible,
                updateVisibleServersUseCase = UpdateServerNamesUseCase(pairing),
                getNewServersFlowUseCase = GetNewServersFlowUseCase(paired, visible),
                localDiscoveryClientRepository = local,
                remoteDiscoveryRepository = remote,
                deviceStateRepository =
                    object : DeviceStateRepository {
                        override val batteryLevel = emptyFlow<Int>()
                        override val signalQuality = emptyFlow<Int>()
                        override val isInternetAvailable = emptyFlow<Boolean>()
                    },
                pairingStorageRepository = pairing,
                localClientDeviceRepository =
                    object : LocalClientDeviceRepository {
                        override val localDevice = emptyFlow<Device.Client>()
                    },
            )
        }
    }

    private class LastCameraSettingsRepository : SettingsRepository {
        private val lastId = MutableStateFlow(cachedCamera.idString)

        override fun <T : Any> flowOf(setting: Setting<T>): Flow<T> = lastId.map { setting.castLastCameraId(it) }

        override suspend fun <T : Any> load(setting: Setting<T>): T = setting.castLastCameraId(lastId.value)

        override suspend fun <T : Any> save(
            setting: Setting<T>,
            value: T,
        ) {
            check(setting == Setting.ClientLastServerId)
            lastId.value = value as String
        }

        override fun <T : Any> saveDelayed(
            setting: Setting<T>,
            value: T,
        ) = error("Unexpected delayed write")

        override suspend fun <T : Any> clear(setting: Setting<T>) = error("Unexpected clear")

        @Suppress("UNCHECKED_CAST")
        private fun <T : Any> Setting<T>.castLastCameraId(value: String): T {
            check(this == Setting.ClientLastServerId)
            return value as T
        }
    }

    private companion object {
        private val cameraId = DeviceId.parse("00000000-0000-0000-0000-000000000001")
        private val otherCameraId = DeviceId.parse("00000000-0000-0000-0000-000000000002")
        private val cachedCamera: Device.Server = Device.LocalServer(cameraId, "Camera", NetworkAddress("127.0.0.1"))
        private val returnedCamera: Device.Server = Device.LocalServer(cameraId, "Camera", NetworkAddress("127.0.0.2"))
        private val otherCamera: Device.Server = Device.LocalServer(otherCameraId, "Other camera", NetworkAddress("127.0.0.3"))
    }
}
