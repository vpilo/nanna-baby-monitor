# Remote Camera via Relay — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow a traveling camera device to register with the relay so monitors can reach it from anywhere, while keeping existing LAN camera support unchanged.

**Architecture:** Camera maintains a persistent `/relay/server` WebSocket to the relay. When a monitor connects, the relay signals the camera which opens a new stream connection. Relay client endpoints renamed from `/relay/{stream}/` to `/relay/client/{stream}/`. ProxySession pairs the two sides.

**Tech Stack:** Ktor WebSockets (server + client), Kotlin coroutines (`CompletableDeferred`, `ConcurrentHashMap`), Koin DI, KMP (commonMain + desktopMain)

---

## File Map

| File | Change |
|------|--------|
| `network/common/src/commonMain/.../Constants.kt` | Add three Duration constants |
| `network/common/src/commonMain/.../RelaySignals.kt` | **New** — signal string constants |
| `network/common/src/commonMain/.../RelayHttpClient.kt` | Add `pingInterval` |
| `network/client/src/commonMain/.../NetworkClient.kt` | Add `pingInterval` |
| `network/server/src/commonMain/.../websockets/VideoStreamingServerWebSocket.kt` | Modify receiver type |
| `network/server/src/commonMain/.../websockets/AudioStreamingServerWebSocket.kt` | Modify receiver type |
| `network/server/src/commonMain/.../websockets/ControlServerWebSocket.kt` | Modify receiver type |
| `network/server/build.gradle.kts` | Add ktor client bundle |
| `network/server/src/commonMain/.../RelayServerRegistration.kt` | **New** |
| `model/src/commonMain/.../repository/NetworkServerRepository.kt` | Add interface methods |
| `network/server/src/commonMain/.../DefaultNetworkServerRepository.kt` | Remove settings dep, add relay registration, use Duration constants |
| `appCommon/src/commonMain/.../server/home/ServerHomeScreenViewModel.kt` | Subscribe to RelayHost + DeviceName |
| `network/relay/src/desktopMain/.../DefaultNetworkRelayRepository.kt` | Major rewrite |
| `network/relay/src/desktopMain/.../ProxySession.kt` | Close both sessions on exit |
| `network/client/src/commonMain/.../WebSocketConnectionHandler.kt` | One-line path fix |

---

### Task 1: Add shared constants to `network/common`

**Files:**
- Modify: `network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/Constants.kt`
- Create: `network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/RelaySignals.kt`
- Modify: `network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/RelayHttpClient.kt`
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkClient.kt`

Three Duration constants centralise the ping period, session timeout, and stop grace period used across server, relay, and client modules. `RelaySignals` is the equivalent of `Endpoints` for the relay's camera-signaling protocol — defined once, used in both `DefaultNetworkRelayRepository` (sender) and `RelayServerRegistration` (receiver).

- [ ] **Step 1: Update `Constants.kt`** — add three Duration constants at the bottom of the object

```kotlin
package org.vpilo.babymonitor.network.common

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

object Constants {
    const val WEBSOCKET_PORT = 47812

    const val DISCOVERY_PORT = 47813

    const val RELAY_PORT = 47814

    const val DISCOVERY_SERVICE_TYPE = "_babymonitor._tcp."
    const val DISCOVERY_SERVICE_DESCRIPTION = "Baby Monitor service"

    const val SERVICES_LISTEN_ADDRESS: String = "0.0.0.0"

    val WEBSOCKET_PING_PERIOD: Duration = 30.seconds
    val WEBSOCKET_TIMEOUT: Duration = 10.seconds
    val SERVER_STOP_GRACE_PERIOD: Duration = 5.seconds
}
```

- [ ] **Step 2: Create `RelaySignals.kt`**

```kotlin
package org.vpilo.babymonitor.network.common

object RelaySignals {
    const val CONTROL = "REQUEST_CONTROL"
    const val AUDIO = "REQUEST_AUDIO"
    const val VIDEO = "REQUEST_VIDEO"
}
```

- [ ] **Step 3: Update `RelayHttpClient.kt`** — add `pingInterval`

```kotlin
package org.vpilo.babymonitor.network.common

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

val relayHttpClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = Constants.WEBSOCKET_PING_PERIOD
        }
        engine {
            https {
                trustManager = RelayTrustManager()
            }
        }
    }
}
```

- [ ] **Step 4: Update `NetworkClient.kt`** — add `pingInterval`

```kotlin
package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

internal val networkClient: HttpClient by lazy {
    HttpClient(CIO) {
        install(WebSockets) {
            pingInterval = Constants.WEBSOCKET_PING_PERIOD
        }
    }
}
```

Note: `NetworkClient.kt` is in `network/client` which already depends on `network/common`, so `Constants` is available without any dependency change.

- [ ] **Step 5: Build to verify**

```bash
./gradlew :network:common:compileKotlinDesktop :network:common:compileAndroidMain \
          :network:client:compileKotlinDesktop :network:client:compileAndroidMain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/Constants.kt \
        network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/RelaySignals.kt \
        network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/RelayHttpClient.kt \
        network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/NetworkClient.kt
git commit -m "feat(network:common): add RelaySignals, Duration constants, and client ping intervals"
```

---

### Task 2: Change streaming WebSocket handler receiver types

**Files:**
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/websockets/VideoStreamingServerWebSocket.kt`
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/websockets/AudioStreamingServerWebSocket.kt`
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/websockets/ControlServerWebSocket.kt`

`DefaultWebSocketServerSession` (server-side) and `DefaultClientWebSocketSession` (relay client-side) both extend `DefaultWebSocketSession`. Changing the receiver to the common base lets `RelayServerRegistration` call these functions from a relay stream connection (a client WebSocket session), while the existing local server usage (`DefaultWebSocketServerSession` extends `DefaultWebSocketSession`) continues to work unchanged.

- [ ] **Step 1: Update `VideoStreamingServerWebSocket.kt`**

Replace `import io.ktor.server.websocket.DefaultWebSocketServerSession` with `import io.ktor.websocket.DefaultWebSocketSession` and change the function signature:

```kotlin
// before
internal suspend fun DefaultWebSocketServerSession.videoStreamingServerWebSocket() {
// after
internal suspend fun DefaultWebSocketSession.videoStreamingServerWebSocket() {
```

- [ ] **Step 2: Update `AudioStreamingServerWebSocket.kt`**

Same substitution:

```kotlin
// before
internal suspend fun DefaultWebSocketServerSession.audioStreamingServerWebSocket() {
// after
internal suspend fun DefaultWebSocketSession.audioStreamingServerWebSocket() {
```

- [ ] **Step 3: Update `ControlServerWebSocket.kt`**

Same substitution:

```kotlin
// before
internal suspend fun DefaultWebSocketServerSession.controlServerWebSocket() {
// after
internal suspend fun DefaultWebSocketSession.controlServerWebSocket() {
```

- [ ] **Step 4: Build to verify**

```bash
./gradlew :network:server:compileKotlinDesktop :network:server:compileAndroidMain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/websockets/
git commit -m "refactor(network:server): use DefaultWebSocketSession receiver in streaming handlers"
```

---

### Task 3: Add ktor client dependency to network:server

**Files:**
- Modify: `network/server/build.gradle.kts`

`RelayServerRegistration` will call `relayHttpClient.wss(...)`. The `wss` extension is from `ktor-client-websockets`, which is in `network:common` as `implementation` (non-transitive). `network:server` needs it declared explicitly.

- [ ] **Step 1: Add `libs.bundles.ktor.client` to `commonMain.dependencies` in `network/server/build.gradle.kts`**

```kotlin
commonMain.dependencies {
    implementation(project(":common"))
    implementation(project(":model"))
    implementation(project(":camera:model"))
    implementation(project(":codec"))
    implementation(project(":settings:model"))
    implementation(project(":network:common"))

    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)

    implementation(libs.koin.core)
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :network:server:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add network/server/build.gradle.kts
git commit -m "build(network:server): add ktor client dependency for relay stream connections"
```

---

### Task 4: Create `RelayServerRegistration`

**Files:**
- Create: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/RelayServerRegistration.kt`

Mirrors `RelayDiscoverySource` on the client side. Connects to `/relay/server`, sends device name, loops reading `RelaySignals.*` frames from the relay and opens a new stream connection for each one. On registration disconnect, cancels active streams and retries after 5s. Passing an empty host or name stops everything.

- [ ] **Step 1: Create the file**

```kotlin
package org.vpilo.babymonitor.network.server

import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.RelaySignals
import org.vpilo.babymonitor.network.common.deriveSharedRelaySecret
import org.vpilo.babymonitor.network.common.relayHttpClient
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.controlServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import kotlin.time.Duration.Companion.seconds

internal class RelayServerRegistration(private val scope: CoroutineScope) {
    private var relayHost: String = ""
    private var deviceName: String = ""
    private var registrationJob: Job? = null
    private val activeStreamJobs = mutableListOf<Job>()

    fun setRelayHost(host: String) {
        relayHost = host
        restart()
    }

    fun setDeviceName(name: String) {
        deviceName = name
        restart()
    }

    private fun restart() {
        registrationJob?.cancel()
        activeStreamJobs.forEach { it.cancel() }
        activeStreamJobs.clear()
        registrationJob = null
        if (relayHost.isEmpty() || deviceName.isEmpty()) return
        registrationJob = scope.launch { runRegistrationLoop() }
    }

    private suspend fun runRegistrationLoop() {
        while (true) {
            @Suppress("TooGenericExceptionCaught")
            try {
                Logger.d(TAG) { "Connecting to relay at $relayHost as '$deviceName'" }
                relayHttpClient.wss(
                    method = HttpMethod.Get,
                    host = relayHost,
                    port = Constants.RELAY_PORT,
                    path = "/relay/server",
                ) {
                    RelayHandshake.send(this, secret)
                    send(Frame.Text(deviceName))
                    Logger.i(TAG) { "Registered with relay as '$deviceName'" }
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            when (frame.readText()) {
                                RelaySignals.CONTROL -> launchStream(Endpoints.CONTROL)
                                RelaySignals.AUDIO -> launchStream(Endpoints.STREAM_AUDIO)
                                RelaySignals.VIDEO -> launchStream(Endpoints.STREAM_VIDEO)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> {
                        Logger.d(TAG) { "Relay registration cancelled" }
                        throw e
                    }
                    else -> {
                        Logger.w(TAG) { "Relay registration disconnected: ${e.message}. Retrying in 5s." }
                        activeStreamJobs.forEach { it.cancel() }
                        activeStreamJobs.clear()
                        delay(5.seconds)
                    }
                }
            }
        }
    }

    private fun launchStream(endpoint: String) {
        val job = scope.launch {
            @Suppress("TooGenericExceptionCaught")
            try {
                relayHttpClient.wss(
                    method = HttpMethod.Get,
                    host = relayHost,
                    port = Constants.RELAY_PORT,
                    path = "/relay/server$endpoint/$deviceName",
                ) {
                    RelayHandshake.send(this, secret)
                    when (endpoint) {
                        Endpoints.CONTROL -> controlServerWebSocket()
                        Endpoints.STREAM_AUDIO -> audioStreamingServerWebSocket()
                        Endpoints.STREAM_VIDEO -> videoStreamingServerWebSocket()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(TAG) { "Relay stream $endpoint failed: ${e.message}" }
            }
        }
        activeStreamJobs.add(job)
        job.invokeOnCompletion { activeStreamJobs.remove(job) }
    }

    private companion object {
        private val TAG = RelayServerRegistration::class
        private val secret by lazy { deriveSharedRelaySecret() }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :network:server:compileKotlinDesktop :network:server:compileAndroidMain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/RelayServerRegistration.kt
git commit -m "feat(network:server): add RelayServerRegistration for remote camera relay support"
```

---

### Task 5: Add `setRelayHost`/`setDeviceName` to interface and refactor `DefaultNetworkServerRepository`

**Files:**
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkServerRepository.kt`
- Modify: `network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/DefaultNetworkServerRepository.kt`

Both files change together to keep the project compilable. The interface gains two methods; the implementation removes the `settingsRepository` constructor parameter (and its `init` block that subscribed to `Setting.DeviceName`), wires up `RelayServerRegistration`, and uses Duration constants from `Constants`.

- [ ] **Step 1: Update `NetworkServerRepository.kt`**

Complete file:

```kotlin
package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow
import org.vpilo.babymonitor.model.CaptureMode

interface NetworkServerRepository {
    val serverStateFlow: Flow<ServerState>

    suspend fun start()

    suspend fun stop()

    suspend fun setCaptureMode(mode: CaptureMode)

    fun setRelayHost(host: String)

    fun setDeviceName(name: String)
}
```

- [ ] **Step 2: Rewrite `DefaultNetworkServerRepository.kt`**

Key changes vs current file:
- Constructor: remove `settingsRepository: SettingsRepository` parameter
- Remove `init` block (subscribed to `Setting.DeviceName`)
- Add `private val relayRegistration = RelayServerRegistration(scope)`
- Implement `setRelayHost` and `setDeviceName`
- Make `scope` private
- WebSockets plugin: replace hardcoded `5.seconds` / `3.seconds` with `Constants.WEBSOCKET_PING_PERIOD` / `Constants.WEBSOCKET_TIMEOUT`
- `stop()`: replace `STOP_GRACE_PERIOD_SECONDS` / `STOP_TIMEOUT_SECONDS` with `Constants.SERVER_STOP_GRACE_PERIOD`

Complete file:

```kotlin
package org.vpilo.babymonitor.network.server

import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.DeviceStateRepository
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.server.websockets.audioStreamingServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.controlServerWebSocket
import org.vpilo.babymonitor.network.server.websockets.videoStreamingServerWebSocket
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

internal class DefaultNetworkServerRepository(
    private val discoveryManager: DiscoveryManager,
    private val deviceStateRepository: DeviceStateRepository,
    private val coroutineContext: CoroutineContext,
) : NetworkServerRepository {
    private var server: EmbeddedServer<*, *>? = null

    private val activeAudioSessions = mutableListOf<WebSocketSession>()
    private val activeVideoSessions = mutableListOf<WebSocketSession>()

    private val state = MutableStateFlow(ServerState())
    override val serverStateFlow: Flow<ServerState> = state.asStateFlow()

    private val currentCaptureMode: CaptureMode
        get() = state.value.captureMode

    private var deviceStateMonitor: Job? = null

    private val scope: CoroutineScope = CoroutineScope(coroutineContext + SupervisorJob())
    private val relayRegistration = RelayServerRegistration(scope)

    override fun setRelayHost(host: String) {
        relayRegistration.setRelayHost(host)
    }

    override fun setDeviceName(name: String) {
        discoveryManager.setDeviceName(name)
        relayRegistration.setDeviceName(name)
    }

    override suspend fun start() {
        if (server != null) return

        scope.launch {
            deviceStateMonitor =
                combine(
                    deviceStateRepository.batteryLevel,
                    deviceStateRepository.signalQuality,
                ) { batteryLevel, signalQuality ->
                    state.update { it.copy(signalQuality = signalQuality, batteryLevel = batteryLevel) }
                }.launchIn(this)
        }

        discoveryManager.registerService()

        scope.launch {
            embeddedServer(
                factory = CIO,
                module = { serverModule() },
                host = Constants.SERVICES_LISTEN_ADDRESS,
                port = Constants.WEBSOCKET_PORT,
            ).apply {
                server = this

                monitor.subscribe(ServerReady) {
                    Logger.i(TAG) { "Server is ready at ${Constants.SERVICES_LISTEN_ADDRESS}" }
                    state.update { it.copy(isAvailable = true) }
                }
                monitor.subscribe(ApplicationStopped) {
                    Logger.i(TAG) { "Server is stopping" }
                    state.update { it.copy(isAvailable = false) }
                    monitor.unsubscribe(ApplicationStarted) {}
                    monitor.unsubscribe(ApplicationStopped) {}
                }
                start(wait = false)
            }
        }

        Logger.i(TAG) { "Requested server start" }
    }

    override suspend fun stop() {
        withContext(coroutineContext) {
            Logger.i(TAG) { "Requested server stop" }
            activeAudioSessions.closeAll()
            activeVideoSessions.closeAll()
            deviceStateMonitor?.cancel()
            deviceStateMonitor = null
            discoveryManager.unregisterService()
            server?.stop(
                shutdownGracePeriod = Constants.SERVER_STOP_GRACE_PERIOD.inWholeSeconds,
                timeout = Constants.SERVER_STOP_GRACE_PERIOD.inWholeSeconds,
                timeUnit = TimeUnit.SECONDS,
            )
            server = null
            state.update { it.copy(isAvailable = false) }
        }
    }

    override suspend fun setCaptureMode(mode: CaptureMode) {
        when (mode) {
            CaptureMode.VIDEO_ONLY -> activeAudioSessions.closeAll()
            CaptureMode.AUDIO_ONLY -> activeVideoSessions.closeAll()
            else -> { /* Nothing to do */ }
        }
        Logger.i(TAG) { "Requested update to $mode" }
        state.update { it.copy(captureMode = mode) }
    }

    private suspend fun MutableList<WebSocketSession>.closeAll() {
        forEach { it.close(CloseReason(CloseReason.Codes.GOING_AWAY, "")) }
        clear()
    }

    private fun Application.serverModule() {
        install(WebSockets) {
            pingPeriod = Constants.WEBSOCKET_PING_PERIOD
            timeout = Constants.WEBSOCKET_TIMEOUT
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }

        routing {
            webSocket(Endpoints.CONTROL) {
                controlServerWebSocket()
            }
            webSocket(Endpoints.STREAM_AUDIO) {
                if (currentCaptureMode == CaptureMode.VIDEO_ONLY) {
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Audio streaming is disabled"))
                    return@webSocket
                }
                activeAudioSessions.add(this)
                try {
                    audioStreamingServerWebSocket()
                } finally {
                    Logger.i(TAG) { "Closed audio session" }
                    activeAudioSessions.remove(this)
                }
            }
            webSocket(Endpoints.STREAM_VIDEO) {
                if (currentCaptureMode == CaptureMode.AUDIO_ONLY) {
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Video streaming is disabled"))
                    return@webSocket
                }
                activeVideoSessions.add(this)
                try {
                    videoStreamingServerWebSocket()
                } finally {
                    Logger.i(TAG) { "Closed video session" }
                    activeVideoSessions.remove(this)
                }
            }
        }
    }

    private companion object {
        private val TAG = DefaultNetworkServerRepository::class
    }
}
```

- [ ] **Step 3: Build to verify**

```bash
./gradlew :model:compileKotlinDesktop :network:server:compileKotlinDesktop :network:server:compileAndroidMain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkServerRepository.kt \
        network/server/src/commonMain/kotlin/org/vpilo/babymonitor/network/server/DefaultNetworkServerRepository.kt
git commit -m "feat(network:server): wire RelayServerRegistration, remove SettingsRepository dep, use Duration constants"
```

---

### Task 6: Update `ServerHomeScreenViewModel` to push settings to server

**Files:**
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreenViewModel.kt`

The ViewModel already receives `settings: SettingsRepository`. Add subscriptions to `Setting.DeviceName` and `Setting.RelayHost` to push values into the server repository (parallel to how `ClientHomeScreenViewModel` does it).

- [ ] **Step 1: Rewrite `ServerHomeScreenViewModel.kt`**

Complete file:

```kotlin
package org.vpilo.babymonitor.app.server.home

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.LastCaptureMode
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.model.repository.NetworkServerRepository
import org.vpilo.babymonitor.model.viewmodel.AppViewModel
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName

class ServerHomeScreenViewModel(
    private val server: NetworkServerRepository,
    private val settings: SettingsRepository,
) : AppViewModel<ServerHomeScreenAction, ServerHomeScreenState, Unit>(
        initialState = ServerHomeScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        server.serverStateFlow.subscribe { serverState ->
            state.copy(isAvailable = serverState.isAvailable, captureMode = serverState.captureMode).update()
        }
        settings.flowOf(Setting.LastCaptureMode).subscribe {
            state.copy(captureMode = it).update()
            server.setCaptureMode(it)
        }
        settings.flowOf(Setting.DeviceName).subscribe { name ->
            server.setDeviceName(name)
        }
        settings.flowOf(Setting.RelayHost).subscribe { host ->
            server.setRelayHost(host)
        }

        vmScope.launch {
            server.start()
        }
    }

    override fun onCleared() {
        vmScope.launch {
            server.stop()
        }
    }

    override fun onAction(action: ServerHomeScreenAction) {
        when (action) {
            is ServerHomeScreenAction.CaptureModeSelected -> {
                vmScope.launch {
                    settings.save(Setting.LastCaptureMode, action.captureMode)
                    server.setCaptureMode(action.captureMode)
                }
            }
        }
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :appCommon:compileKotlinDesktop :appCommon:compileAndroidMain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/server/home/ServerHomeScreenViewModel.kt
git commit -m "feat(appCommon): push relay host and device name settings to server repository"
```

---

### Task 7: Update relay server — rename client endpoints and add remote camera support

**Files:**
- Modify: `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/DefaultNetworkRelayRepository.kt`

Three changes in one commit:
1. Client endpoints renamed: `/relay/{stream}/{serverId}` → `/relay/client/{stream}/{serverId}`
2. Discovery combines `currentServers` (mDNS) and `remoteServers` (registered cameras); local takes precedence
3. New `/relay/server` endpoint for camera registration; new `/relay/server/{stream}/{serverId}` endpoints for rendezvous

Also: WebSocket plugin migrated from `pingPeriodMillis`/`timeoutMillis` (Long) to `pingPeriod`/`timeout` (Duration) using `Constants`, and `stop()` uses `Constants.SERVER_STOP_GRACE_PERIOD`. `RelaySignals.*` replaces the private string constants.

**Rendezvous protocol:**
- Monitor connects to `/relay/client/{stream}/{id}` → relay enqueues a `CompletableDeferred<WebSocketServerSession?>` and sends `RelaySignals.*` to the camera's registration session
- Camera opens `/relay/server/{stream}/{id}` → relay dequeues the deferred, completes it with the camera's session, then awaits session close
- Relay calls `ProxySession.run(client = monitorSession, server = cameraSession)` to wire them together

`pendingRelays` key format: `"$serverId:$endpoint"` e.g. `"nursery:/video"`

- [ ] **Step 1: Replace `DefaultNetworkRelayRepository.kt` with the new implementation**

Complete file:

```kotlin
package org.vpilo.babymonitor.network.relay

import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import java.net.InetAddress
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.RelaySignals
import org.vpilo.babymonitor.network.common.relayHttpClient

class DefaultNetworkRelayRepository(
    private val discoveryManager: DiscoveryManager,
    private val config: RelayConfig,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private var server: EmbeddedServer<*, *>? = null

    private val currentServers = MutableStateFlow<Set<DiscoveredServer>>(emptySet())
    private val remoteServers = MutableStateFlow<Map<String, WebSocketServerSession>>(emptyMap())

    // Key: "$serverId:$endpoint" e.g. "nursery:/video"
    private val pendingRelays = ConcurrentHashMap<String, ConcurrentLinkedDeque<CompletableDeferred<WebSocketServerSession?>>>()

    fun start() {
        if (server != null) return
        discoveryManager.discoveredServers
            .onEach { currentServers.value = it }
            .launchIn(scope)

        val keyStore = loadKeyStore()

        server =
            embeddedServer(
                factory = Netty,
                configure = {
                    sslConnector(
                        keyStore = keyStore,
                        keyAlias = KEYSTORE_ALIAS,
                        keyStorePassword = { KEYSTORE_PASSWORD.toCharArray() },
                        privateKeyPassword = { KEYSTORE_PASSWORD.toCharArray() },
                    ) {
                        port = config.port
                    }
                },
                module = {
                    install(WebSockets) {
                        pingPeriod = Constants.WEBSOCKET_PING_PERIOD
                        timeout = Constants.WEBSOCKET_TIMEOUT
                    }
                    routing {
                        webSocket("/relay/discovery") {
                            Logger.i(TAG) { "Client connected to discovery endpoint" }
                            if (!RelayHandshake.await(this, config.secret)) {
                                close()
                                return@webSocket
                            }
                            combine(currentServers, remoteServers) { local, remote ->
                                val localNames = local.map { it.id.name }.toSet()
                                localNames + remote.keys.filter { it !in localNames }
                            }.collect { names ->
                                Logger.i(TAG) { "Server list: $names" }
                                send(names.joinToString("\n"))
                            }
                        }

                        webSocket("/relay/client${Endpoints.CONTROL}/{serverId}") {
                            Logger.i(TAG) { "Monitor connected to control endpoint" }
                            handleClientEndpoint(Endpoints.CONTROL)
                        }

                        webSocket("/relay/client${Endpoints.STREAM_AUDIO}/{serverId}") {
                            Logger.i(TAG) { "Monitor connected to audio stream endpoint" }
                            handleClientEndpoint(Endpoints.STREAM_AUDIO)
                        }

                        webSocket("/relay/client${Endpoints.STREAM_VIDEO}/{serverId}") {
                            Logger.i(TAG) { "Monitor connected to video stream endpoint" }
                            handleClientEndpoint(Endpoints.STREAM_VIDEO)
                        }

                        webSocket("/relay/server") {
                            if (!RelayHandshake.await(this, config.secret)) {
                                close()
                                return@webSocket
                            }
                            val cameraName = (incoming.receive() as? Frame.Text)?.readText() ?: run {
                                close()
                                return@webSocket
                            }
                            remoteServers.update { it + (cameraName to this) }
                            Logger.i(TAG) { "Camera '$cameraName' registered" }
                            try {
                                for (frame in incoming) { /* drain to detect disconnect */ }
                            } finally {
                                remoteServers.update { it - cameraName }
                                pendingRelays.keys
                                    .filter { it.startsWith("$cameraName:") }
                                    .forEach { key ->
                                        pendingRelays.remove(key)?.forEach { deferred ->
                                            deferred.complete(null)
                                        }
                                    }
                                Logger.i(TAG) { "Camera '$cameraName' unregistered" }
                            }
                        }

                        webSocket("/relay/server${Endpoints.CONTROL}/{serverId}") {
                            handleCameraStreamEndpoint(Endpoints.CONTROL)
                        }

                        webSocket("/relay/server${Endpoints.STREAM_AUDIO}/{serverId}") {
                            handleCameraStreamEndpoint(Endpoints.STREAM_AUDIO)
                        }

                        webSocket("/relay/server${Endpoints.STREAM_VIDEO}/{serverId}") {
                            handleCameraStreamEndpoint(Endpoints.STREAM_VIDEO)
                        }
                    }
                },
            ).start(wait = false)

        Logger.i(TAG) { "Relay started on port ${config.port}" }
    }

    private suspend fun DefaultWebSocketServerSession.handleClientEndpoint(endpoint: String) {
        if (!RelayHandshake.await(this, config.secret)) {
            close()
            return
        }
        val serverId = call.parameters["serverId"] ?: run { close(); return }

        // Local camera: proxy outbound to the camera's own server
        val localAddress = currentServers.value
            .firstOrNull { it.id.name == serverId }
            ?.addresses
            ?.firstOrNull()

        if (localAddress != null) {
            relayHttpClient.webSocket(
                method = HttpMethod.Get,
                host = localAddress.hostAddress,
                port = Constants.WEBSOCKET_PORT,
                path = endpoint,
            ) {
                ProxySession.run(client = this@handleClientEndpoint, server = this)
            }
            return
        }

        // Remote camera: signal it to open a new stream connection
        val registrationSession = remoteServers.value[serverId] ?: run {
            send(Frame.Text("Server not found: $serverId"))
            close()
            return
        }

        val relayKey = "$serverId:$endpoint"
        val cameraArrived = CompletableDeferred<WebSocketServerSession?>()
        pendingRelays.getOrPut(relayKey) { ConcurrentLinkedDeque() }.addLast(cameraArrived)

        try {
            val signal = when (endpoint) {
                Endpoints.CONTROL -> RelaySignals.CONTROL
                Endpoints.STREAM_AUDIO -> RelaySignals.AUDIO
                Endpoints.STREAM_VIDEO -> RelaySignals.VIDEO
                else -> error("Unknown endpoint: $endpoint")
            }
            registrationSession.send(signal)

            val cameraSession = withTimeoutOrNull(Constants.WEBSOCKET_TIMEOUT.inWholeMilliseconds) { cameraArrived.await() }
                ?: run {
                    close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Camera unavailable"))
                    return
                }

            ProxySession.run(client = this, server = cameraSession)
        } finally {
            pendingRelays[relayKey]?.remove(cameraArrived)
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleCameraStreamEndpoint(endpoint: String) {
        if (!RelayHandshake.await(this, config.secret)) {
            close()
            return
        }
        val serverId = call.parameters["serverId"] ?: run { close(); return }
        val relayKey = "$serverId:$endpoint"

        val deferred = pendingRelays[relayKey]?.pollFirst() ?: run {
            close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "No pending client for $serverId/$endpoint"))
            return
        }
        deferred.complete(this)
        closeReason.await()
    }

    fun stop() {
        server?.stop(
            gracePeriodMillis = Constants.SERVER_STOP_GRACE_PERIOD.inWholeMilliseconds,
            timeoutMillis = Constants.SERVER_STOP_GRACE_PERIOD.inWholeMilliseconds,
        )
        server = null
        relayHttpClient.close()
        Logger.i(TAG) { "Relay stopped" }
    }

    private fun loadKeyStore(): KeyStore {
        val stream =
            checkNotNull(
                DefaultNetworkRelayRepository::class.java.classLoader.getResourceAsStream(KEYSTORE_RESOURCE),
            ) { "relay.p12 not found in resources" }
        return stream.use { s ->
            KeyStore.getInstance("PKCS12").apply {
                load(s, KEYSTORE_PASSWORD.toCharArray())
            }
        }
    }

    private companion object {
        private val TAG = DefaultNetworkRelayRepository::class
        private const val KEYSTORE_RESOURCE = "relay.p12"
        private const val KEYSTORE_PASSWORD = "babymonitor"
        private const val KEYSTORE_ALIAS = "relay"
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :network:relay:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/DefaultNetworkRelayRepository.kt
git commit -m "feat(network:relay): add remote camera registration and rendezvous, rename client endpoints, use Duration constants"
```

---

### Task 8: Update `ProxySession` to close both sessions on completion

**Files:**
- Modify: `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/ProxySession.kt`

Currently `run()` returns when one side disconnects without explicitly closing the other session. The relay camera-side handler (`handleCameraStreamEndpoint`) uses `closeReason.await()` to stay alive while ProxySession is running; it only exits when its session closes. Adding explicit `server.close()` and `client.close()` ensures the camera-side session is signaled when the monitor side ends.

- [ ] **Step 1: Rewrite `ProxySession.kt`**

Complete file:

```kotlin
package org.vpilo.babymonitor.network.relay

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal object ProxySession {
    suspend fun run(
        client: WebSocketSession,
        server: WebSocketSession,
    ) {
        try {
            coroutineScope {
                launch {
                    try {
                        for (frame in client.incoming) {
                            server.send(frame)
                        }
                    } finally {
                        coroutineContext.cancel()
                    }
                }
                launch {
                    try {
                        for (frame in server.incoming) {
                            client.send(frame)
                        }
                    } finally {
                        coroutineContext.cancel()
                    }
                }
            }
        } catch (_: CancellationException) {
            // Normal close — one side dropped
        }
        server.close()
        client.close()
    }
}
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :network:relay:compileKotlinDesktop
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/ProxySession.kt
git commit -m "fix(network:relay): explicitly close both WebSocket sessions when ProxySession ends"
```

---

### Task 9: Update client relay endpoint path

**Files:**
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/WebSocketConnectionHandler.kt`

One-line change. The relay renamed its client endpoints from `/relay/{stream}/...` to `/relay/client/{stream}/...`.

- [ ] **Step 1: Update the path string in `WebSocketConnectionHandler.kt`**

Find the `wss {}` block (around line 50) and change the `path` parameter:

```kotlin
// before
path = "/relay$endpointPath/${serverId.name}",
// after
path = "/relay/client$endpointPath/${serverId.name}",
```

- [ ] **Step 2: Build to verify**

```bash
./gradlew :network:client:compileKotlinDesktop :network:client:compileAndroidMain
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/WebSocketConnectionHandler.kt
git commit -m "fix(network:client): update relay path prefix to /relay/client"
```

---

### Task 10: Full project build verification

- [ ] **Step 1: Build all targets**

```bash
./gradlew :appDesktop:desktopJar :appAndroid:assembleDebug
```

Expected: `BUILD SUCCESSFUL` for both targets.

---

## Self-Review

**Spec coverage check:**

| Requirement | Task |
|-------------|------|
| Camera connects to relay when `RelayHost` is configured | T4 (auto-connects on non-empty host+name) |
| No new user-facing settings | ✓ No new settings added |
| Local cameras (mDNS) unchanged | T7 (`handleClientEndpoint` checks `currentServers` first) |
| Local takes precedence when camera on relay's LAN | T7 (local address check before remote path) |
| Empty host stops registration | T4 (`restart()` returns early if empty) |
| Stream on demand; camera encodes only when monitor watches | T4 (stream connections opened only on `RelaySignals.*`) |
| Multiple monitors → multiple streams | T7 (each monitor enqueues its own deferred, triggers one signal) |
| Rendezvous timeout | T7 (`withTimeoutOrNull(Constants.WEBSOCKET_TIMEOUT)`) |
| Cancel pending relays on camera disconnect | T7 (`finally` block calls `deferred.complete(null)`) |
| Camera retries relay on disconnect | T4 (`runRegistrationLoop` catches exceptions, delays 5s, loops) |
| Relay client endpoint rename (both sides) | T7 (relay) + T9 (client) |
| `/relay/discovery` path unchanged | ✓ Path unchanged |
| Camera stream functions work on client-side sessions | T2 (receiver changed to `DefaultWebSocketSession`) |
| ProxySession closes both sides | T8 |
| Ping periods unified | T1 (`Constants.WEBSOCKET_PING_PERIOD` used in server, relay, both HTTP clients) |
| Timeouts unified | T1 + T5 + T7 (`Constants.WEBSOCKET_TIMEOUT`) |
| Stop grace periods unified | T5 + T7 (`Constants.SERVER_STOP_GRACE_PERIOD`) |
| `RelaySignals` defined once | T1 (`network/common`), used in T4 and T7 |

**Type consistency:**
- `RelaySignals.CONTROL/AUDIO/VIDEO` referenced consistently in `RelayServerRegistration` (T4) and `DefaultNetworkRelayRepository` (T7)
- `CompletableDeferred<WebSocketServerSession?>` used consistently in `pendingRelays` and `handleCameraStreamEndpoint`
- `Endpoints.CONTROL`, `Endpoints.STREAM_AUDIO`, `Endpoints.STREAM_VIDEO` used as path segments throughout
- `Constants.WEBSOCKET_TIMEOUT` reused as rendezvous timeout in T7 (10s — camera has one timeout period to connect after receiving the signal)
