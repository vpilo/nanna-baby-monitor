package org.vpilo.babymonitor.app.server.home

import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.vpilo.babymonitor.camera.model.VideoCaptureRepository
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.Device
import org.vpilo.babymonitor.model.VideoStream
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.network.model.repository.LocalDiscoveryServerRepository
import org.vpilo.babymonitor.network.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.network.model.repository.RelayConfigurationRepository
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceId
import org.vpilo.babymonitor.settings.model.settings.DeviceName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class ServerHomeScreenViewModelTest {
    @Test
    fun networkOutageRefreshesDiscoveryWithoutReleasingTheRunningServer() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            val store = ViewModelStore()
            val connectivity = MutableStateFlow(true)
            val server = RunningServer()
            val discovery = LocalDiscovery()
            try {
                val viewModel =
                    ServerHomeScreenViewModel(
                        discoveryServer = discovery,
                        server = server,
                        settings = CameraSettings(),
                        relayConfigurationRepository =
                            object : RelayConfigurationRepository {
                                override val relayConfiguration = emptyFlow<Nothing>()
                            },
                        deviceStateRepository =
                            object : DeviceStateRepository {
                                override val batteryLevel = emptyFlow<Int>()
                                override val signalQuality = emptyFlow<Int>()
                                override val isInternetAvailable = connectivity
                            },
                        videoCaptureRepository =
                            object : VideoCaptureRepository {
                                override val videoStream =
                                    object : VideoStream<Unit>() {
                                        override val surface = emptyFlow<Unit?>()
                                        override val isActive = flowOf(false)
                                    }
                            },
                    )
                store.put("server-home", viewModel)
                runCurrent()
                val originalServer = assertNotNull(server.instance)
                assertEquals("Test camera", discovery.device?.name)

                connectivity.value = false
                runCurrent()
                assertNull(discovery.device)
                assertSame(originalServer, server.instance)

                connectivity.value = true
                runCurrent()
                assertEquals("Test camera", discovery.device?.name)
                assertSame(originalServer, server.instance)

                store.clear()
                assertNull(discovery.device)
                assertNull(server.instance)
            } finally {
                store.clear()
                Dispatchers.resetMain()
            }
        }

    private class RunningServer : NetworkServerRepository {
        var instance: Device.LocalServer? = null
            private set

        override val serverStateFlow = emptyFlow<Nothing>()
        override val pairingState = emptyFlow<Nothing>()
        override val relayVersionMismatchFlow = emptyFlow<Nothing>()

        override suspend fun start(self: Device.LocalServer) {
            if (instance == null) instance = self
        }

        override suspend fun stop() {
            instance = null
        }

        override suspend fun setCaptureMode(mode: CaptureMode) = Unit

        override fun startPairingWindow() = Unit

        override fun cancelPairingWindow() = Unit
    }

    private class LocalDiscovery : LocalDiscoveryServerRepository {
        var device: Device.Server? = null
            private set

        override fun register(device: Device.Server) {
            this.device = device
        }

        override fun unregister() {
            device = null
        }
    }

    private class CameraSettings : SettingsRepository {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> flowOf(setting: Setting<T>): Flow<T> =
            when (setting) {
                Setting.DeviceId -> flowOf("11111111-1111-1111-1111-111111111111")
                Setting.DeviceName -> flowOf("Test camera")
                else -> error("Unexpected setting: $setting")
            } as Flow<T>

        override suspend fun <T : Any> load(setting: Setting<T>): T = error("Unexpected settings load")

        override suspend fun <T : Any> save(
            setting: Setting<T>,
            value: T,
        ) = error("Unexpected settings save")

        override fun <T : Any> saveDelayed(
            setting: Setting<T>,
            value: T,
        ) = error("Unexpected settings save")

        override suspend fun <T : Any> clear(setting: Setting<T>) = error("Unexpected settings clear")
    }
}
