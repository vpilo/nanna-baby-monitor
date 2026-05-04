# Streaming Lifecycle Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Couple each medium's WebSocket lifetime to actual subscriber count by moving WebSocket session ownership from `DefaultNetworkClientRepository` into `NetworkVideoReceiverRepository` and `NetworkAudioReceiverRepository`, eliminating the wasted bandwidth that occurs when `CameraFeed` is detached.

**Architecture:** Introduce a `ConnectionTargetDataSource` Koin singleton that holds the `(address, serverId)` tuple. `NetworkVideoReceiverRepository` and `NetworkAudioReceiverRepository` (which already extend `SharedResourceHolder`) gain `WebSocketConnectionHandler` ownership, observe the target, and open/close their handlers in `start()`/`stop()`. `DefaultNetworkClientRepository` becomes control-only and writes the target. `ClientHomeScreenViewModel` gates its `frames` flow by `(setting && server.isAvailable && captureMode != AUDIO_ONLY)` and drives audio playback from a derived `(setting && server.isAvailable && captureMode != VIDEO_ONLY)` gate.

**Tech Stack:** Kotlin Multiplatform (Android + Desktop/JVM), Ktor WebSockets, Koin DI, Compose, MVVM.

**Spec corrections applied during planning:**

- `SettingsRepository.save(...)` (immediate, `suspend`) already exists at `settings/model/.../SettingsRepository.kt:11` — the spec's "Pre-requisite work" item is dropped. Because `save` is `suspend`, the `ToggleAudio`/`ToggleVideo` action handlers wrap the call in `vmScope.launch { … }`.
- The spec's note about migrating callers of `SharedResourceHolder.isActive: Boolean` is moot — there are no existing callers (verified by grep across the project).
- The receiver repo's `start()`/`stop()` snippets in the spec have a latent race (`Dispatchers.Default` lets the target collector run concurrently with `stop()`, so a freshly nulled `handler` could be re-assigned before `targetJob.cancel()` is called). Implementation uses a `try { … } finally { handler?.disconnect(); handler = null }` pattern around `target.collect { … }`, and `stop()` only cancels `targetJob` and stops the decoder. This makes handler cleanup the responsibility of the cancelled coroutine itself.
- `flatMapLatest` requires `@OptIn(ExperimentalCoroutinesApi::class)` on coroutines 1.10.2.
- The spec says "`appCommonKoinModule`: `ClientHomeScreenViewModel` constructor gains `StreamingAudioReceiverRepository`" — but `viewModelOf(::ClientHomeScreenViewModel)` auto-resolves all params, so no Koin module change is needed; only the VM constructor changes.

---

## File Structure

**Create:**
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/ConnectionTargetDataSource.kt`

**Modify:**
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/SharedResourceHolder.kt` — `isActive: Boolean` → `isActive: Flow<Boolean>`
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/StreamingVideoReceiverRepository.kt` — add `isActive: Flow<Boolean>`
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/StreamingAudioReceiverRepository.kt` — add `isActive: Flow<Boolean>`
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/di/NetworkClientKoinModule.kt` — register `ConnectionTargetDataSource`
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkVideoReceiverRepository.kt` — own video WebSocket
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkAudioReceiverRepository.kt` — own audio WebSocket
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt` — strip stream lifecycle, write target
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenViewModel.kt` — gates, state, action handlers, ctor
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt` — drop `enableAudio`/`enableVideo`
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkControlDataSource.kt` — drop `setIsStreaming*`
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ServerState.kt` — drop `isStreamingAudio`/`isStreamingVideo`

---

### Task 1: Add `ConnectionTargetDataSource`

A new Koin singleton that holds the `(address, serverId)` pair. Writers: the network client repo. Readers: the receiver repos. Until later tasks wire the writes, the target stays `null` and nothing observes the data source — pure additive change.

**Files:**
- Create: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/ConnectionTargetDataSource.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/di/NetworkClientKoinModule.kt`

- [ ] **Step 1: Create `ConnectionTargetDataSource.kt`**

Write:

```kotlin
package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.model.repository.ServerId
import java.net.InetAddress

internal data class ConnectionTarget(
    val address: InetAddress,
    val serverId: ServerId,
)

internal class ConnectionTargetDataSource {
    private val collector = MutableStateFlow<ConnectionTarget?>(null)
    val target: StateFlow<ConnectionTarget?> = collector.asStateFlow()

    fun set(target: ConnectionTarget?) {
        collector.value = target
    }
}
```

- [ ] **Step 2: Register in `NetworkClientKoinModule`**

Edit `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/di/NetworkClientKoinModule.kt`. Add the import and the singleton declaration:

```kotlin
import org.vpilo.babymonitor.network.client.ConnectionTargetDataSource
```

In the `module { ... }` body, add `singleOf(::ConnectionTargetDataSource)` next to the other data-source registrations:

```kotlin
val networkClientKoinModule: Module =
    module {
        singleOf(::NetworkControlDataSource)
        singleOf(::ConnectionTargetDataSource)
        singleOf(::NetworkAudioDataSource)
        singleOf(::NetworkVideoDataSource)
        singleOf(::RelayDiscoveryDataSource)

        singleOf(::NetworkAudioReceiverRepository)
            .bind<StreamingAudioReceiverRepository>()
        singleOf(::NetworkVideoReceiverRepository)
            .bind<StreamingVideoReceiverRepository>()

        singleOf(::DefaultNetworkClientRepository)
            .bind<NetworkClientRepository>()

        singleOf(::DiscoveryManager)
            .withOptions { createdAtStart() }
    }
```

- [ ] **Step 3: Compile**

Run:

```sh
./gradlew :network:client:compileKotlinDesktop :network:client:compileAndroidMain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```sh
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/ConnectionTargetDataSource.kt \
        network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/di/NetworkClientKoinModule.kt
git commit -m "Add ConnectionTargetDataSource singleton

Holds the (address, serverId) tuple. Receiver repositories will
observe it to drive their stream WebSocket lifetime; the network
client repository will write it on control connection open/close."
```

---

### Task 2: Convert `SharedResourceHolder.isActive` to `Flow<Boolean>` and expose on receiver interfaces

`isActive: Boolean` (snapshot) becomes `isActive: Flow<Boolean>` (observable), so the VM can `combine(...)` it with other flows to derive `isAudioPlaying` / `isVideoPlaying`. Verified: no existing callers of the old Boolean. The reactor inside `SharedResourceHolder` continues to work — it uses a separate collection of `subscriptionCount`, unrelated to the new exposed `isActive`.

**Files:**
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/SharedResourceHolder.kt`
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/StreamingVideoReceiverRepository.kt`
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/StreamingAudioReceiverRepository.kt`

- [ ] **Step 1: Update `SharedResourceHolder.kt`**

Replace the existing `isActive` property. Add the imports `kotlinx.coroutines.flow.Flow`, `kotlinx.coroutines.flow.distinctUntilChanged`, `kotlinx.coroutines.flow.map` at the top. Replace lines 35–36:

```kotlin
    val isActive: Boolean
        get() = collector.subscriptionCount.value > 0
```

with:

```kotlin
    val isActive: Flow<Boolean> =
        collector.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()
```

Final file contents:

```kotlin
package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.ktx.reactor
import kotlin.reflect.KClass

/**
 * Base class for a repository that manages a shared resource, such as a camera.
 *
 * This assumes the shared resource generates a flow of [T] objects when in use.
 * [start] when the first subscriber starts using the [collector], and [stop] is called when the last subscriber stops using it.
 *
 * The [bufferCapacity] determines the [collector]'s buffer, and the [onBufferOverflow] strategy determines how to handle buffer overflows,
 * e.g. slow subscribers.
 */
abstract class SharedResourceHolder<T>(
    protected val coroutineDispatcher: CoroutineDispatcher = Dispatchers.Default,
    bufferCapacity: Int = 0,
    onBufferOverflow: BufferOverflow = BufferOverflow.DROP_OLDEST,
) {
    protected val coroutineScope = CoroutineScope(coroutineDispatcher)

    protected val collector: MutableSharedFlow<T> =
        MutableSharedFlow(0, bufferCapacity, onBufferOverflow = onBufferOverflow)

    init {
        collector.reactor(coroutineScope, onActive = ::onActive, onInactive = ::onInactive)
    }

    val isActive: Flow<Boolean> =
        collector.subscriptionCount
            .map { it > 0 }
            .distinctUntilChanged()

    protected abstract fun start()

    protected abstract fun stop()

    private fun onActive() {
        Logger.d(TAG) { "Starting" }
        start()
    }

    private fun onInactive() {
        Logger.d(TAG) { "Stopping" }
        stop()
    }

    @Suppress("VariableNaming", "ktlint:standard:property-naming")
    open val TAG: KClass<*> = this::class
}
```

- [ ] **Step 2: Update `StreamingVideoReceiverRepository.kt`**

Add `isActive` to the interface:

```kotlin
package org.vpilo.babymonitor.model.repository

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Repository that receives encoded video chunks, decodes them,
 * and exposes decoded frames as a flow of [ImageBitmap].
 */
interface StreamingVideoReceiverRepository {
    val decodedFrames: SharedFlow<ImageBitmap>
    val isActive: Flow<Boolean>
}
```

- [ ] **Step 3: Update `StreamingAudioReceiverRepository.kt`**

Add `isActive` to the interface:

```kotlin
package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.AudioFrameFlow

/**
 * Repository that provides decoded audio chunks.
 */
interface StreamingAudioReceiverRepository {
    val chunks: AudioFrameFlow
    val isActive: Flow<Boolean>
}
```

The `NetworkVideoReceiverRepository` and `NetworkAudioReceiverRepository` implementations satisfy the new interface contract automatically — they inherit `isActive: Flow<Boolean>` from the updated `SharedResourceHolder`.

- [ ] **Step 4: Compile (whole project)**

Run:

```sh
./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. (Other `SharedResourceHolder` subclasses — capture data sources, sender repos — don't reference `isActive`, so this is a clean compile.)

- [ ] **Step 5: Commit**

```sh
git add model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/SharedResourceHolder.kt \
        model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/StreamingVideoReceiverRepository.kt \
        model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/StreamingAudioReceiverRepository.kt
git commit -m "Make SharedResourceHolder.isActive a Flow<Boolean>

Expose it on the streaming receiver repository interfaces so that
the client view model can combine it into derived UI state. No
existing callers used the old Boolean snapshot."
```

---

### Task 3: Receiver repositories observe the target and own their stream WebSockets

Each receiver repo gains a `ConnectionTargetDataSource` constructor parameter and, in `start()`, launches a coroutine that observes `target` and (re)creates its `WebSocketConnectionHandler` whenever the target changes. Until Task 4 starts writing the target, this task is a behavioral no-op (target stays `null`, no handler comes up). The legacy WebSocket lifecycle in `DefaultNetworkClientRepository` continues to drive streams in this intermediate state.

The `try { target.collect { … } } finally { handler.disconnect() }` pattern makes handler cleanup the responsibility of the launched coroutine itself. `stop()` only needs to cancel that coroutine and stop the decoder — no race with the target collector re-creating a handler after `stop()` clears the field.

**Files:**
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkVideoReceiverRepository.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkAudioReceiverRepository.kt`

- [ ] **Step 1: Rewrite `NetworkVideoReceiverRepository.kt`**

```kotlin
package org.vpilo.babymonitor.network.client

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.codec.VideoDecoder
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import kotlin.coroutines.CoroutineContext

internal class NetworkVideoReceiverRepository(
    dataSource: NetworkVideoDataSource,
    private val connectionTargetDataSource: ConnectionTargetDataSource,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<ImageBitmap>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_FRAME_BUFFER_SIZE,
    ),
    StreamingVideoReceiverRepository {
    override val decodedFrames: SharedFlow<ImageBitmap> = collector.asSharedFlow()

    private val decoder: VideoDecoder =
        VideoDecoder(
            input = dataSource.frames,
            output = collector,
            coroutineContext = coroutineContext,
        )

    private var handler: WebSocketConnectionHandler? = null
    private var targetJob: Job? = null

    override fun start() {
        decoder.start()
        targetJob =
            coroutineScope.launch {
                try {
                    connectionTargetDataSource.target.collect { target ->
                        handler?.disconnect()
                        handler = null
                        if (target == null) return@collect
                        handler =
                            WebSocketConnectionHandler(
                                hosts = setOf(target.address),
                                endpointPath = Endpoints.STREAM_VIDEO,
                                serverId = target.serverId,
                                sessionBlock = { _ -> videoStreamingClientWebSocket() },
                                onDisconnected = {
                                    Logger.i(TAG) { "Video disconnected, reconnecting" }
                                    delay(Constants.RECONNECTION_TIMEOUT)
                                    handler?.connect()
                                },
                                coroutineScope = coroutineScope,
                            ).apply { connect() }
                    }
                } finally {
                    handler?.disconnect()
                    handler = null
                }
            }
    }

    override fun stop() {
        targetJob?.cancel()
        targetJob = null
        decoder.stop()
    }
}
```

- [ ] **Step 2: Rewrite `NetworkAudioReceiverRepository.kt`**

```kotlin
package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.codec.AudioDecoder
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.AudioFrame
import org.vpilo.babymonitor.model.AudioFrameFlow
import org.vpilo.babymonitor.model.MediaFormats
import org.vpilo.babymonitor.model.repository.SharedResourceHolder
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import kotlin.coroutines.CoroutineContext

internal class NetworkAudioReceiverRepository(
    dataSource: NetworkAudioDataSource,
    private val connectionTargetDataSource: ConnectionTargetDataSource,
    coroutineContext: CoroutineContext,
) : SharedResourceHolder<AudioFrame>(
        bufferCapacity = MediaFormats.BufferSizes.MAX_SAMPLE_BUFFER_SIZE,
    ),
    StreamingAudioReceiverRepository {
    override val chunks: AudioFrameFlow = collector.asSharedFlow()

    private val decoder: AudioDecoder =
        AudioDecoder(
            input = dataSource.audioFrames,
            output = collector,
            coroutineContext = coroutineContext,
        )

    private var handler: WebSocketConnectionHandler? = null
    private var targetJob: Job? = null

    override fun start() {
        decoder.start()
        targetJob =
            coroutineScope.launch {
                try {
                    connectionTargetDataSource.target.collect { target ->
                        handler?.disconnect()
                        handler = null
                        if (target == null) return@collect
                        handler =
                            WebSocketConnectionHandler(
                                hosts = setOf(target.address),
                                endpointPath = Endpoints.STREAM_AUDIO,
                                serverId = target.serverId,
                                sessionBlock = { _ -> audioStreamingClientWebSocket() },
                                onDisconnected = {
                                    Logger.i(TAG) { "Audio disconnected, reconnecting" }
                                    delay(Constants.RECONNECTION_TIMEOUT)
                                    handler?.connect()
                                },
                                coroutineScope = coroutineScope,
                            ).apply { connect() }
                    }
                } finally {
                    handler?.disconnect()
                    handler = null
                }
            }
    }

    override fun stop() {
        targetJob?.cancel()
        targetJob = null
        decoder.stop()
    }
}
```

- [ ] **Step 3: Compile**

Run:

```sh
./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. Koin auto-resolves the new `ConnectionTargetDataSource` constructor parameter (registered in Task 1).

- [ ] **Step 4: Commit**

```sh
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkVideoReceiverRepository.kt \
        network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkAudioReceiverRepository.kt
git commit -m "Move stream WebSocket ownership into receiver repositories

Each receiver repository now observes ConnectionTargetDataSource
inside its SharedResourceHolder start()/stop() and owns its
WebSocketConnectionHandler. The target is still null in this commit
(legacy lifecycle in DefaultNetworkClientRepository continues to
operate); the next commit cuts over the writer side."
```

---

### Task 4: Strip `DefaultNetworkClientRepository` and rebuild `ClientHomeScreenViewModel`

This is the cutover task: `DefaultNetworkClientRepository` stops creating per-stream handlers and instead writes to `ConnectionTargetDataSource`; the VM stops calling `enableAudio` / `enableVideo` and instead exposes a gated `frames` flow plus a `shouldPlayAudio` gate that drives `PlayReceivedAudioUseCase.toggle`. Both files must change in a single commit because the VM still reads `serverState.isStreamingAudio` and the repo still implements `enableAudio` — the interface and `ServerState` cleanup is deferred to Task 5.

The `enableAudio` / `enableVideo` overrides on the repo are kept temporarily as no-ops so the interface contract stays satisfied without touching the interface in this task. `ServerState.isStreamingAudio` / `isStreamingVideo` keep their default `false` value forever after this task — harmless because the VM no longer reads them.

**Files:**
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenViewModel.kt`

- [ ] **Step 1: Rewrite `DefaultNetworkClientRepository.kt`**

```kotlin
package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.common.ktx.prettify
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketException
import javax.net.ssl.SSLException
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkClientRepository(
    private val discoveryManager: DiscoveryManager,
    private val networkControlDataSource: NetworkControlDataSource,
    private val connectionTargetDataSource: ConnectionTargetDataSource,
    private val relayDiscoveryDataSource: RelayDiscoveryDataSource,
    private val coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val connectionState: MutableStateFlow<NetworkState> =
        MutableStateFlow(NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<NetworkState> = connectionState.asStateFlow()

    override val serverStateFlow: Flow<ServerState> = networkControlDataSource.serverState

    private val localServers = MutableStateFlow<Set<DiscoveredServer>>(emptySet())

    override val discoveredServerIdsFlow: Flow<Set<ServerId>> =
        combine(
            localServers.map { set -> set.map { it.id }.toSet() },
            relayDiscoveryDataSource.serverIds,
        ) { localIds, relayIds ->
            val localNames = localIds.map { it.name }.toSet()
            localIds + relayIds.filter { it.name !in localNames }
        }

    private var relayHost: String = ""
    private var lastConnectedServerId: ServerId? = null

    private var controlHandler: WebSocketConnectionHandler? = null

    init {
        discoveryManager.discoveredServers
            .onEach { localServers.value = it }
            .launchIn(scope)
    }

    override suspend fun connect(server: ServerId) {
        val isRelay = !server.isLocalServer
        val localServer = if (!isRelay) localServers.value.firstOrNull { it.id.name == server.name } else null

        if (!isRelay && localServer == null) {
            Logger.w(TAG) { "Server ${server.name} not found in local servers." }
            connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
            return
        }

        lastConnectedServerId = server
        closeAllConnections()

        val hosts =
            if (isRelay) {
                setOf(InetAddress.getByAddress(relayHost, ByteArray(4)))
            } else {
                checkNotNull(localServer).addresses
            }

        Logger.i(TAG) { "Connecting to server ${server.name} (local: ${server.isLocalServer})" }
        controlHandler =
            WebSocketConnectionHandler(
                hosts = hosts,
                endpointPath = Endpoints.CONTROL,
                serverId = server,
                onDisconnected = { onControlConnectionClosed(it) },
                sessionBlock = { address ->
                    onControlConnectionOpened(server, address)
                    controlClientWebSocket()
                },
                coroutineScope = scope,
            ).apply { connect() }

        connectionState.value = NetworkState.Connecting(server)
    }

    override suspend fun reconnect() {
        val last = lastConnectedServerId
        if (last == null) {
            Logger.w(TAG) { "No server to reconnect to." }
            return
        }
        Logger.i(TAG) { "Reconnecting to ${last.name}" }
        connect(last)
    }

    private fun closeAllConnections() {
        connectionTargetDataSource.set(null)
        controlHandler?.disconnect()
        controlHandler = null
    }

    override fun enableAudio(enable: Boolean) {
        // No-op: audio WebSocket lifetime is now driven by NetworkAudioReceiverRepository's subscriber count.
    }

    override fun enableVideo(enable: Boolean) {
        // No-op: video WebSocket lifetime is now driven by NetworkVideoReceiverRepository's subscriber count.
    }

    override suspend fun disconnect() {
        closeAllConnections()
        connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    override fun setRelayHost(host: String) {
        relayHost = host
        relayDiscoveryDataSource.updateRelayHost(host, scope)
    }

    override fun setDeviceName(name: String) {
        discoveryManager.setDeviceName(name)
    }

    private fun onControlConnectionOpened(
        server: ServerId,
        address: InetAddress,
    ) {
        connectionTargetDataSource.set(ConnectionTarget(address, server))
        connectionState.value = NetworkState.Connected(server)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private fun onControlConnectionClosed(exception: Throwable) {
        closeAllConnections()

        connectionState.value =
            when (exception) {
                is ConnectException -> {
                    Logger.i(TAG) { "Connection refused." }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
                }

                is ClosedReceiveChannelException,
                is CancellationException,
                    -> {
                        Logger.i(TAG) { "Connection closed by client." }
                        NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
                    }

                is SocketException, is SSLException -> {
                    Logger.i(TAG) { "Connection closed: ${exception.prettify()}" }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ConnectionFailed, exception)
                }

                else -> {
                    Logger.w(TAG) { "WebSocket failed: ${exception.prettify()}" }
                    NetworkState.Disconnected(NetworkState.ErrorReason.ServerQuit, exception)
                }
            }
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private companion object {
        private val TAG = DefaultNetworkClientRepository::class
    }
}
```

Note: the `DefaultNetworkClientRepository` constructor now takes `ConnectionTargetDataSource` (Koin auto-resolves). The `enableAudio` / `enableVideo` overrides remain for now — Task 5 removes them along with the interface methods.

- [ ] **Step 2: Rewrite `ClientHomeScreenViewModel.kt`**

```kotlin
package org.vpilo.babymonitor.app.client.home

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.ClientEnabledAudio
import org.vpilo.babymonitor.app.settings.ClientEnabledVideo
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.StreamingAudioReceiverRepository
import org.vpilo.babymonitor.model.repository.StreamingVideoReceiverRepository
import org.vpilo.babymonitor.model.usecase.PlayReceivedAudioUseCase
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

@OptIn(ExperimentalCoroutinesApi::class)
class ClientHomeScreenViewModel(
    private val audioReceiverRepository: StreamingAudioReceiverRepository,
    private val videoReceiverRepository: StreamingVideoReceiverRepository,
    private val networkClientRepository: NetworkClientRepository,
    private val playReceivedAudio: PlayReceivedAudioUseCase,
    private val settingsRepository: SettingsRepository,
) : AppViewModel<ClientHomeScreenAction, ClientHomeScreenState, Unit>(
        initialState = ClientHomeScreenState(),
    ) {
    private val videoGate: Flow<Boolean> =
        combine(
            settingsRepository.flowOf(Setting.ClientEnabledVideo),
            networkClientRepository.serverStateFlow,
        ) { enabled, server ->
            enabled && server.isAvailable && server.captureMode != CaptureMode.AUDIO_ONLY
        }.distinctUntilChanged()

    private val shouldPlayAudio: Flow<Boolean> =
        combine(
            settingsRepository.flowOf(Setting.ClientEnabledAudio),
            networkClientRepository.serverStateFlow,
        ) { enabled, server ->
            enabled && server.isAvailable && server.captureMode != CaptureMode.VIDEO_ONLY
        }.distinctUntilChanged()

    val frames: Flow<ImageBitmap> =
        videoGate.flatMapLatest { open ->
            if (open) videoReceiverRepository.decodedFrames else emptyFlow()
        }

    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.connectionStateFlow.subscribe { netState ->
            Logger.d(TAG) { "Network state changed: $netState" }
            state.copy(networkState = netState).update()
        }

        combine(
            audioReceiverRepository.isActive,
            videoReceiverRepository.isActive,
            networkClientRepository.serverStateFlow,
        ) { isAudioPlaying, isVideoPlaying, serverState ->
            Logger.d(TAG) {
                "Server state changed: $serverState (audio=$isAudioPlaying, video=$isVideoPlaying)"
            }
            state
                .copy(
                    captureMode = serverState.captureMode,
                    batteryLevel = serverState.batteryLevel,
                    signalQuality = serverState.signalQuality,
                    isAudioPlaying = isAudioPlaying,
                    isVideoPlaying = isVideoPlaying,
                ).update()
        }.collectLatest()

        shouldPlayAudio.subscribe { should ->
            if (should == playReceivedAudio.isPlaying.value) return@subscribe
            Logger.d(TAG) {
                "Audio playback should be: $should (was ${playReceivedAudio.isPlaying.value})"
            }
            playReceivedAudio.toggle(vmScope)
        }

        settingsRepository.flowOf(Setting.DeviceName).subscribe { name ->
            networkClientRepository.setDeviceName(name)
        }

        settingsRepository.flowOf(Setting.RelayHost).subscribe { host ->
            networkClientRepository.setRelayHost(host)
        }
    }

    override suspend fun onUnsubscribed() {
        networkClientRepository.disconnect()
    }

    fun disconnect() {
        vmScope.launch {
            networkClientRepository.disconnect()
        }
    }

    override fun onAction(action: ClientHomeScreenAction) {
        when (action) {
            ClientHomeScreenAction.ToggleAudio -> {
                vmScope.launch {
                    settingsRepository.save(Setting.ClientEnabledAudio, !state.isAudioPlaying)
                }
            }

            ClientHomeScreenAction.ToggleVideo -> {
                vmScope.launch {
                    settingsRepository.save(Setting.ClientEnabledVideo, !state.isVideoPlaying)
                }
            }

            ClientHomeScreenAction.Reconnect -> {
                vmScope.launch {
                    networkClientRepository.reconnect()
                }
            }
        }
    }
}
```

Constructor parameter ordering note: `audioReceiverRepository` comes before `videoReceiverRepository` to match the `combine(audioReceiverRepository.isActive, videoReceiverRepository.isActive, …)` ordering used downstream; both are auto-wired by `viewModelOf(::ClientHomeScreenViewModel)`.

- [ ] **Step 3: Compile**

Run:

```sh
./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. The `enableAudio` / `enableVideo` interface methods still exist (now no-ops on the impl); the VM no longer calls them. `ServerState.isStreamingAudio` / `isStreamingVideo` still exist (no longer read).

- [ ] **Step 4: Commit**

```sh
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt \
        appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/client/home/ClientHomeScreenViewModel.kt
git commit -m "Cut over to subscriber-driven stream WebSocket lifecycle

DefaultNetworkClientRepository now writes ConnectionTargetDataSource
on control connect/disconnect and no longer creates per-stream
WebSocketConnectionHandler instances. ClientHomeScreenViewModel
gates its frames flow by (setting && server.isAvailable && captureMode
!= AUDIO_ONLY) so subscribing only happens when the screen is visible
and the server can serve video; audio playback is driven by the
analogous gate via PlayReceivedAudioUseCase.toggle.

ToggleAudio/ToggleVideo actions now flip the corresponding setting
immediately (suspending save), and the gates pick the change up
from the settings flow. enableAudio/enableVideo on the repository
are temporarily no-ops; ServerState.isStreaming* fields are no
longer read. Both are removed in the next commit."
```

---

### Task 5: Remove dead interface and state members

`enableAudio` / `enableVideo` on `NetworkClientRepository`, the `setIsStreamingAudio` / `setIsStreamingVideo` methods on `NetworkControlDataSource`, and the `isStreamingAudio` / `isStreamingVideo` fields on `ServerState` are now unread/unwritten and can be deleted. The audit (verified during planning) confirms no other modules reference them.

**Files:**
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkControlDataSource.kt`
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ServerState.kt`

- [ ] **Step 1: Drop `enableAudio` / `enableVideo` from the interface**

Final `NetworkClientRepository.kt`:

```kotlin
package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface NetworkClientRepository {
    val connectionStateFlow: Flow<NetworkState>

    val serverStateFlow: Flow<ServerState>

    val discoveredServerIdsFlow: Flow<Set<ServerId>>

    suspend fun connect(server: ServerId)

    suspend fun reconnect()

    suspend fun disconnect()

    fun setRelayHost(host: String)

    fun setDeviceName(name: String)
}
```

- [ ] **Step 2: Drop the no-op overrides on `DefaultNetworkClientRepository`**

Delete the `enableAudio` and `enableVideo` overrides added in Task 4 (the lines between `closeAllConnections()` and `disconnect()` blocks).

- [ ] **Step 3: Drop the streaming setters from `NetworkControlDataSource`**

Final `NetworkControlDataSource.kt`:

```kotlin
package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vpilo.babymonitor.model.repository.ServerState

internal class NetworkControlDataSource {
    private val collector: MutableStateFlow<ServerState> =
        MutableStateFlow(ServerState())

    val serverState: StateFlow<ServerState> = collector.asStateFlow()

    internal suspend fun onServerStateReceived(state: ServerState) {
        collector.value =
            collector.value.copy(
                isAvailable = state.isAvailable,
                captureMode = state.captureMode,
                batteryLevel = state.batteryLevel,
                signalQuality = state.signalQuality,
            )
    }
}
```

(Removed: `setIsStreamingAudio`, `setIsStreamingVideo`, the `Logger` import, the `private companion object { TAG }` since nothing logs from this class anymore.)

- [ ] **Step 4: Drop `isStreamingAudio` / `isStreamingVideo` from `ServerState`**

Final `ServerState.kt`:

```kotlin
package org.vpilo.babymonitor.model.repository

import org.vpilo.babymonitor.model.CaptureMode

data class ServerState(
    val isAvailable: Boolean = false,
    val captureMode: CaptureMode = CaptureMode.AUDIO_AND_VIDEO,
    val batteryLevel: Int = 100,
    val signalQuality: Int = 100,
)
```

- [ ] **Step 5: Compile**

Run:

```sh
./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug
```

Expected: `BUILD SUCCESSFUL`. If anything fails to compile, the audit missed a reference — search for the symbol and remove the stale call.

- [ ] **Step 6: Commit**

```sh
git add model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt \
        model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/ServerState.kt \
        network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt \
        network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkControlDataSource.kt
git commit -m "Remove dead enableAudio/enableVideo and isStreaming* members

NetworkClientRepository no longer exposes enableAudio/enableVideo;
NetworkControlDataSource no longer exposes setIsStreamingAudio/Video;
ServerState no longer carries isStreamingAudio/isStreamingVideo. All
were observable hooks for the imperative WebSocket lifecycle that
has been replaced by subscriber-driven receiver repositories."
```

---

### Task 6: Manual verification

The project has no automated tests. Walk through the scenarios in the design's behavior table on desktop (and on Android if practical) and verify each.

**Files:** none modified.

- [ ] **Step 1: Start the server (camera) on one machine**

Run: `./gradlew :appDesktop:run` on the camera machine, choose **Server** mode, start capture.

- [ ] **Step 2: Start the client (monitor) on another machine, both toggles on**

Run: `./gradlew :appDesktop:run` on the client machine, choose **Client** mode, connect to the server.

Verify in the client logs (look for `NetworkVideoReceiverRepository`, `NetworkAudioReceiverRepository`, `WebSocketConnectionHandler`):
- both stream WebSockets open exactly once
- video frames are decoding (UI shows the feed)
- audio is playing

Verify on the server (`NetworkServerRepository` logs): both stream sockets accepted exactly once.

- [ ] **Step 3: Toggle video off on the client**

Click the video toggle button. Verify:
- video WebSocket closes (look for "Video disconnected" in the client log; "Video stream stopped" or equivalent on the server)
- audio continues uninterrupted
- UI button reflects the off state

- [ ] **Step 4: Toggle video back on**

Click the video toggle button. Verify:
- video WebSocket re-opens; frames resume
- audio still uninterrupted

- [ ] **Step 5: Toggle audio off, then back on**

Same checks as Steps 3–4 but for the audio stream.

- [ ] **Step 6: Switch capture mode on the server to `AUDIO_ONLY`**

On the server, change `LastCaptureMode` to AUDIO_ONLY (e.g., via the settings UI or restarting in the appropriate mode). Verify:
- client closes its video WebSocket within ~1 server tick
- audio continues
- the UI's `isVideoPlaying` flips to false; the video toggle button visually reflects "off" / disabled per `captureMode != AUDIO_ONLY`

- [ ] **Step 7: Switch capture mode to `VIDEO_ONLY`**

Verify symmetric behavior — audio WebSocket closes, video opens (if it had been off due to AUDIO_ONLY).

- [ ] **Step 8: Switch capture mode to `AUDIO_AND_VIDEO`**

Verify both WebSockets re-open according to the user's enable settings.

- [ ] **Step 9: Background/foreground the client**

Navigate away from the home screen (e.g., open the menu or another screen) so `CameraFeed` detaches via `LifecycleResumeEffect.onPauseOrDispose`. Verify:
- video WebSocket closes (this is the original bug from the spec)
- audio continues (because `playbackJob` is bound to `vmScope`, not the Compose lifecycle)

Navigate back. Verify the video WebSocket re-opens and frames resume.

- [ ] **Step 10: Disconnect from the server**

Click "Disconnect" on the home screen, or kill the server. Verify:
- target is cleared
- both stream WebSockets close
- `disconnected_reconnecting` overlay appears (or whatever the disconnected UI is)

- [ ] **Step 11: Reconnect**

Restart the server (if it was killed) and let auto-reconnect run, or click reconnect. Verify both WebSockets re-open if the corresponding gate is open.

- [ ] **Step 12: Smoke-test on Android**

Install the debug APK (`./gradlew :appAndroid:installDebug`) and repeat at least Steps 2–4 and 9. The Android lifecycle path through `LifecycleResumeEffect` is the most likely to surface platform-specific issues.

- [ ] **Step 13: If everything passes, finish**

No commit — verification only. If issues are found, capture the symptom, root-cause, and revisit the relevant task before declaring the refactor done.

---

## Known limitations (not blocking — accept; revisit if observed)

- **Server-facing flap.** Rapid resume/pause cycles or fast capture-mode changes flap the per-medium WebSocket open/closed. The spec accepts this; ship without debounce.
- **Trailing reconnect race in receiver repo.** The `onDisconnected` lambda captures the field reference `handler?.connect()`. If `stop()` runs and a fresh `start()` reassigns `handler` before the `delay(RECONNECTION_TIMEOUT)` completes, the lambda calls the new handler's `connect()`. The new handler also calls `connect()` itself, but `WebSocketConnectionHandler.connect()` short-circuits if `connectionJob?.isActive == true`, so the duplicate is a no-op.
- **Reentrance race on rapid stop/start.** `stop()` calls `targetJob?.cancel()` which is asynchronous; the cancelled job's `finally { handler?.disconnect(); handler = null }` runs on `Dispatchers.Default` at some later point. If `start()` is called before that finally runs, the new start can assign a fresh handler that the old job's finally then disconnects and nulls. Net effect: after a rapid stop/start cycle, the stream may not come up until a subsequent target change triggers a new connect. Subscription-count flips are driven by Compose lifecycle and tend to be debounced, so this is unlikely to fire in practice. If observed, fix by routing `WebSocketConnectionHandler.coroutineScope` and the `targetJob` through a single-threaded dispatcher (e.g., `Dispatchers.Main.immediate`) or by making `stop()` use `runBlocking { targetJob?.cancelAndJoin() }` (the latter blocks the reactor coroutine, so prefer the former).
