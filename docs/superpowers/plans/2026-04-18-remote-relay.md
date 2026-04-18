# Remote Relay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a self-hosted JVM relay app that proxies audio/video/control streams over the internet, and extend the client app to discover and connect through it.

**Architecture:** New `network:relay` library + `appRelay` entry point; existing `DiscoveryManager.desktop.kt` reused for mDNS; Ktor TLS server on port 47814; client gets two discovery flows and routes `connect()` to `ConnectionHandler` (LAN) or `RelayConnectionHandler` (relay) based on which list the server is in.

**Tech Stack:** Ktor 3.4.1, JmDNS 3.6.3 (existing), `java.security` (TLS/cert pinning), Koin 4.1.1, SHA-256 (JDK built-in)

**Reference spec:** `docs/superpowers/specs/2026-04-18-remote-relay-design.md`

---

## File Map

**Create:**
- `network/relay/build.gradle.kts`
- `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/RelayConfig.kt`
- `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/ProxySession.kt`
- `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/DefaultNetworkRelayRepository.kt`
- `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/di/NetworkRelayKoinModule.kt`
- `appRelay/build.gradle.kts`
- `appRelay/src/desktopMain/kotlin/org/vpilo/babymonitor/relay/Main.kt`
- `appRelay/src/desktopMain/resources/relay.p12` (generated binary)
- `network/client/src/commonMain/resources/relay.crt` (generated)
- `network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/RelayHandshake.kt`
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayTrustManager.kt`
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayDiscoverySource.kt`
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayConnectionHandler.kt`

**Modify:**
- `settings.gradle.kts` — add `:network:relay`, `:appRelay`
- `network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/Constants.kt` — add `RELAY_PORT`
- `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt` — replace `discoveredServerIdsFlow` with `localServerIdsFlow` + `relayServerIdsFlow`
- `appCommon/src/commonMain/composeResources/values/strings.xml` — relay host strings
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/settings/AppSettings.kt` — `Setting.RelayHost`
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/menu/AppMenuContents.kt` — relay host menu item
- `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt` — two discovery sources, relay routing
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreenState.kt`
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreenViewModel.kt`
- `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreen.kt`

---

## Task 1: Generate TLS certificate

**Files:**
- Create: `appRelay/src/desktopMain/resources/relay.p12`
- Create: `network/client/src/commonMain/resources/relay.crt`

- [ ] **Step 1: Create resource directories**

```bash
mkdir -p appRelay/src/desktopMain/resources
mkdir -p network/client/src/commonMain/resources
```

- [ ] **Step 2: Generate self-signed cert + private key**

```bash
openssl req -x509 -newkey rsa:4096 \
  -keyout /tmp/relay-key.pem \
  -out /tmp/relay-cert.pem \
  -days 3650 \
  -nodes \
  -subj "/CN=babymonitor-relay"
```

Expected: two PEM files in /tmp with no passphrase prompt.

- [ ] **Step 3: Convert to PKCS12 keystore for the relay server**

```bash
openssl pkcs12 -export \
  -out appRelay/src/desktopMain/resources/relay.p12 \
  -inkey /tmp/relay-key.pem \
  -in /tmp/relay-cert.pem \
  -name relay \
  -passout pass:babymonitor
```

Expected: `appRelay/src/desktopMain/resources/relay.p12` created.

- [ ] **Step 4: Copy public cert for client pinning**

```bash
cp /tmp/relay-cert.pem network/client/src/commonMain/resources/relay.crt
```

Expected: `network/client/src/commonMain/resources/relay.crt` exists.

- [ ] **Step 5: Verify files**

```bash
openssl pkcs12 -info -in appRelay/src/desktopMain/resources/relay.p12 -passin pass:babymonitor -noout
openssl x509 -in network/client/src/commonMain/resources/relay.crt -noout -subject -enddate
```

Expected: no errors; subject shows `CN=babymonitor-relay`; enddate is ~10 years out.

- [ ] **Step 6: Commit**

```bash
git add appRelay/src/desktopMain/resources/relay.p12
git add network/client/src/commonMain/resources/relay.crt
git commit -m "feat: add pre-generated TLS certificate for relay"
```

---

## Task 2: Register new modules

**Files:**
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Add modules to settings.gradle.kts**

In `settings.gradle.kts`, after `include(":network:server")`, add:

```kotlin
include(":network:relay")
```

After `include(":appDesktop")`, add:

```kotlin
include(":appRelay")
```

- [ ] **Step 2: Compile (expect empty project errors)**

```bash
./gradlew :network:relay:tasks 2>&1 | head -5
```

Expected: error about missing build.gradle.kts — that's fine, confirms the module is registered.

---

## Task 3: network:relay build file

**Files:**
- Create: `network/relay/build.gradle.kts`

- [ ] **Step 1: Create build file**

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.dependencies {
            implementation(project(":common"))
            implementation(project(":network:common"))

            implementation(libs.bundles.ktor.server)
            implementation(libs.bundles.ktor.client)
            implementation(libs.koin.core)
        }
    }
}
```

Note: `ktor.server` is needed to serve the relay WebSocket endpoints; `ktor.client` is needed to open proxy connections to local servers.

- [ ] **Step 2: Sync and verify**

```bash
./gradlew :network:relay:tasks --quiet 2>&1 | head -10
```

Expected: task list prints without error.

---

## Task 4: appRelay build file

**Files:**
- Create: `appRelay/build.gradle.kts`

- [ ] **Step 1: Create build file**

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.dependencies {
            implementation(project(":network:relay"))
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.jvm)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.vpilo.babymonitor.relay.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb)
            packageName = "org.vpilo.babymonitor.relay"
            packageVersion = "1.0.0"
        }
    }
}
```

- [ ] **Step 2: Sync and verify**

```bash
./gradlew :appRelay:tasks --quiet 2>&1 | head -10
```

Expected: task list includes `run`.

---

## Task 5: network:common additions

**Files:**
- Modify: `network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/Constants.kt`
- Create: `network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/RelayHandshake.kt`

- [ ] **Step 1: Add RELAY_PORT to Constants.kt**

In `network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/Constants.kt`, add after `WEBSOCKET_PORT`:

```kotlin
const val RELAY_PORT = 47814
```

- [ ] **Step 2: Create RelayHandshake.kt**

```kotlin
package org.vpilo.babymonitor.network.common

import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readBytes
import kotlinx.coroutines.withTimeoutOrNull
import java.security.MessageDigest

private const val HANDSHAKE_SIZE = 256
private const val HANDSHAKE_TIMEOUT_MS = 2_000L

fun deriveSecret(password: String): ByteArray {
    val hash = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
    return ByteArray(HANDSHAKE_SIZE) { hash[it % hash.size] }
}

object RelayHandshake {
    suspend fun await(session: WebSocketSession, secret: ByteArray): Boolean {
        val frame = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
            session.incoming.receive()
        } ?: return false
        val data = frame.readBytes()
        if (data.size != HANDSHAKE_SIZE) return false
        return data.contentEquals(secret)
    }

    suspend fun send(session: WebSocketSession, secret: ByteArray) {
        session.send(Frame.Binary(fin = true, data = secret))
    }
}
```

- [ ] **Step 3: Compile network:common**

```bash
./gradlew :network:common:compileKotlinDesktop --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/Constants.kt
git add network/common/src/commonMain/kotlin/org/vpilo/babymonitor/network/common/RelayHandshake.kt
git commit -m "feat: add RELAY_PORT constant and RelayHandshake to network:common"
```

---

## Task 6: network:relay library

**Files:**
- Create: `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/RelayConfig.kt`
- Create: `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/ProxySession.kt`
- Create: `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/DefaultNetworkRelayRepository.kt`
- Create: `network/relay/src/desktopMain/kotlin/org/vpilo/babymonitor/network/relay/di/NetworkRelayKoinModule.kt`

- [ ] **Step 1: Create RelayConfig.kt**

```kotlin
package org.vpilo.babymonitor.network.relay

data class RelayConfig(
    val port: Int,
    val secret: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RelayConfig) return false
        return port == other.port && secret.contentEquals(other.secret)
    }

    override fun hashCode(): Int = 31 * port + secret.contentHashCode()
}
```

- [ ] **Step 2: Create ProxySession.kt**

```kotlin
package org.vpilo.babymonitor.network.relay

import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal object ProxySession {
    suspend fun run(client: WebSocketSession, server: WebSocketSession) {
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
    }
}
```

- [ ] **Step 3: Create DefaultNetworkRelayRepository.kt**

```kotlin
package org.vpilo.babymonitor.network.relay

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.cio.CIO as ServerCIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets as ServerWebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import java.security.KeyStore
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

private const val KEYSTORE_RESOURCE = "relay.p12"
private const val KEYSTORE_PASSWORD = "babymonitor"
private const val KEYSTORE_ALIAS = "relay"

class DefaultNetworkRelayRepository(
    private val discoveryManager: DiscoveryManager,
    private val config: RelayConfig,
    coroutineContext: CoroutineContext,
) {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)
    private var server: EmbeddedServer<*, *>? = null

    private val currentServers = MutableStateFlow<Set<DiscoveredServer>>(emptySet())

    private val localClient = HttpClient(CIO) {
        install(WebSockets)
    }

    fun start() {
        discoveryManager.discoveredServers
            .onEach { currentServers.value = it }
            .launchIn(scope)

        val keyStore = loadKeyStore()

        server = embeddedServer(
            factory = ServerCIO,
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
                install(ServerWebSockets) {
                    pingPeriod = 30.seconds
                    timeout = 10.seconds
                }
                routing {
                    webSocket("/relay/discovery") {
                        if (!RelayHandshake.await(this, config.secret)) { close(); return@webSocket }
                        currentServers.collect { servers ->
                            val names = servers.joinToString("\n") { it.id.name }
                            send(names)
                        }
                    }

                    webSocket("/relay${Endpoints.CONTROL}/{serverId}") {
                        proxyEndpoint(Endpoints.CONTROL)
                    }

                    webSocket("/relay${Endpoints.STREAM_AUDIO}/{serverId}") {
                        proxyEndpoint(Endpoints.STREAM_AUDIO)
                    }

                    webSocket("/relay${Endpoints.STREAM_VIDEO}/{serverId}") {
                        proxyEndpoint(Endpoints.STREAM_VIDEO)
                    }
                }
            },
        ).start(wait = false)

        Logger.i(TAG) { "Relay started on port ${config.port}" }
    }

    private suspend fun io.ktor.server.websocket.WebSocketServerSession.proxyEndpoint(endpoint: String) {
        if (!RelayHandshake.await(this, config.secret)) { close(); return }
        val serverId = call.parameters["serverId"] ?: run { close(); return }
        val serverAddress = currentServers.value
            .firstOrNull { it.id.name == serverId }
            ?.addresses?.firstOrNull()
            ?: run {
                send(Frame.Text("Server not found: $serverId"))
                close()
                return
            }

        localClient.webSocket(
            method = HttpMethod.Get,
            host = serverAddress.hostAddress,
            port = Constants.WEBSOCKET_PORT,
            path = endpoint,
        ) {
            ProxySession.run(client = this@proxyEndpoint, server = this)
        }
    }

    fun stop() {
        server?.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
        server = null
        localClient.close()
        Logger.i(TAG) { "Relay stopped" }
    }

    private companion object {
        private val TAG = DefaultNetworkRelayRepository::class

        fun loadKeyStore(): KeyStore {
            val stream = checkNotNull(
                DefaultNetworkRelayRepository::class.java.classLoader.getResourceAsStream(KEYSTORE_RESOURCE)
            ) { "relay.p12 not found in resources" }
            return KeyStore.getInstance("PKCS12").apply {
                load(stream, KEYSTORE_PASSWORD.toCharArray())
            }
        }
    }
}
```

- [ ] **Step 4: Create NetworkRelayKoinModule.kt**

```kotlin
package org.vpilo.babymonitor.network.relay.di

import org.koin.core.module.Module
import org.koin.dsl.module
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.deriveSecret
import org.vpilo.babymonitor.network.relay.DefaultNetworkRelayRepository
import org.vpilo.babymonitor.network.relay.RelayConfig

private const val RELAY_PASSWORD = "babymonitor-relay-secret"

val networkRelayKoinModule: Module = module {
    single {
        RelayConfig(
            port = Constants.RELAY_PORT,
            secret = deriveSecret(RELAY_PASSWORD),
        )
    }
    single { DiscoveryManager() }
    single { DefaultNetworkRelayRepository(get(), get(), get()) }
}
```

- [ ] **Step 5: Compile network:relay**

```bash
./gradlew :network:relay:compileKotlinDesktop --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add network/relay/
git commit -m "feat: add network:relay library"
```

---

## Task 7: appRelay entry point

**Files:**
- Create: `appRelay/src/desktopMain/kotlin/org/vpilo/babymonitor/relay/Main.kt`

- [ ] **Step 1: Create Main.kt**

```kotlin
package org.vpilo.babymonitor.relay

import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.vpilo.babymonitor.network.relay.DefaultNetworkRelayRepository
import org.vpilo.babymonitor.network.relay.di.networkRelayKoinModule

fun main() {
    val koin = startKoin {
        modules(networkRelayKoinModule)
    }.koin

    val relay = koin.get<DefaultNetworkRelayRepository>()
    relay.start()

    println("Relay running. Press Enter to stop.")
    runBlocking { readLine() }

    relay.stop()
}
```

- [ ] **Step 2: Compile appRelay**

```bash
./gradlew :appRelay:compileKotlinDesktop --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add appRelay/
git commit -m "feat: add appRelay entry point"
```

---

## Task 8: Client TLS trust manager

**Files:**
- Create: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayTrustManager.kt`

- [ ] **Step 1: Create RelayTrustManager.kt**

`javax.net.ssl.*` and `java.security.cert.*` are available in `commonMain` on both JVM and Android — no expect/actual needed.

```kotlin
package org.vpilo.babymonitor.network.client

import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

internal class RelayTrustManager : X509TrustManager {
    private val pinnedCert: X509Certificate = loadPinnedCert()

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        if (chain.isNullOrEmpty()) throw CertificateException("No certificate chain")
        if (!chain[0].encoded.contentEquals(pinnedCert.encoded)) {
            throw CertificateException("Relay certificate does not match pinned certificate")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private companion object {
        fun loadPinnedCert(): X509Certificate {
            val stream = checkNotNull(
                RelayTrustManager::class.java.classLoader.getResourceAsStream("relay.crt")
            ) { "relay.crt not found in resources" }
            return CertificateFactory.getInstance("X.509")
                .generateCertificate(stream) as X509Certificate
        }
    }
}
```

- [ ] **Step 2: Compile network:client**

```bash
./gradlew :network:client:compileKotlinDesktop --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Compile for Android**

```bash
./gradlew :network:client:compileAndroidMain --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayTrustManager.kt
git commit -m "feat: add RelayTrustManager with cert pinning"
```

---

## Task 9: Setting.RelayHost + menu UI

**Files:**
- Modify: `appCommon/src/commonMain/composeResources/values/strings.xml`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/settings/AppSettings.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/menu/AppMenuContents.kt`

- [ ] **Step 1: Add string resources**

In `appCommon/src/commonMain/composeResources/values/strings.xml`, add before `</resources>`:

```xml
    <string name="relay_host_title">Remote relay hostname</string>
    <string name="relay_host_description">DDNS hostname for remote access (e.g. myhome.dyndns.com)</string>
```

- [ ] **Step 2: Add Setting.RelayHost to AppSettings.kt**

In `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/settings/AppSettings.kt`, add at the bottom. First add the import at the top:

```kotlin
import babymonitor.appcommon.generated.resources.relay_host_description
import babymonitor.appcommon.generated.resources.relay_host_title
```

Then add at the bottom of the file:

```kotlin
val Setting.Companion.RelayHost by makeSetting {
    Setting.makePrimitive(
        id = SettingId("relay_host"),
        name = Res.string.relay_host_title,
        description = Res.string.relay_host_description,
        default = "",
    )
}
```

- [ ] **Step 3: Add menu item to AppMenuContents.kt**

In `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/menu/AppMenuContents.kt`:

Add import at the top:
```kotlin
import androidx.compose.material.icons.filled.Cloud
import org.vpilo.babymonitor.app.settings.RelayHost
```

Add at the bottom of `AppMenuContents`, before the `if (PlatformAvailability.DesktopOnly...)` block:

```kotlin
    if (currentRole == AppRole.CLIENT) {
        MenuSettingItem(
            setting = Setting.RelayHost,
            imageVector = Icons.Default.Cloud,
        )
    }
```

- [ ] **Step 4: Compile appCommon**

```bash
./gradlew :appCommon:compileKotlinDesktop --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add appCommon/src/commonMain/composeResources/values/strings.xml
git add appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/settings/AppSettings.kt
git add appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/menu/AppMenuContents.kt
git commit -m "feat: add RelayHost setting and menu item"
```

---

## Task 10: RelayDiscoverySource

**Files:**
- Create: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayDiscoverySource.kt`

- [ ] **Step 1: Create RelayDiscoverySource.kt**

`RelayDiscoverySource` connects to the relay discovery endpoint, authenticates, and emits the current set of available server IDs. It reconnects automatically on failure.

```kotlin
package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.RelayHandshake
import kotlin.time.Duration.Companion.seconds

internal class RelayDiscoverySource(
    private val secret: ByteArray,
) {
    private val _serverIds = MutableStateFlow<Set<ServerId>>(emptySet())
    val serverIds: StateFlow<Set<ServerId>> = _serverIds.asStateFlow()

    private var relayHost: String = ""
    private var discoveryJob: Job? = null

    private val client: HttpClient by lazy {
        HttpClient(CIO) {
            install(WebSockets)
            engine {
                https {
                    trustManager = RelayTrustManager()
                }
            }
        }
    }

    fun updateRelayHost(host: String, scope: CoroutineScope) {
        relayHost = host
        discoveryJob?.cancel()
        _serverIds.value = emptySet()
        if (host.isEmpty()) return
        discoveryJob = scope.launch { runDiscoveryLoop() }
    }

    private suspend fun runDiscoveryLoop() {
        while (true) {
            try {
                client.wss(
                    method = HttpMethod.Get,
                    host = relayHost,
                    port = Constants.RELAY_PORT,
                    path = "/relay/discovery",
                ) {
                    RelayHandshake.send(this, secret)
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val ids = frame.readText()
                                .lines()
                                .filter { it.isNotEmpty() }
                                .map { ServerId(it) }
                                .toSet()
                            _serverIds.value = ids
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.w(TAG) { "Relay discovery disconnected: ${e.message}. Retrying in 5s." }
                _serverIds.value = emptySet()
                delay(5.seconds)
            }
        }
    }

    private companion object {
        private val TAG = RelayDiscoverySource::class
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :network:client:compileKotlinDesktop --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayDiscoverySource.kt
git commit -m "feat: add RelayDiscoverySource"
```

---

## Task 11: RelayConnectionHandler

**Files:**
- Create: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayConnectionHandler.kt`

- [ ] **Step 1: Create RelayConnectionHandler.kt**

Mirrors `ConnectionHandler` but with no multi-address fallback — a single relay host is tried and reconnected by the caller.

```kotlin
package org.vpilo.babymonitor.network.client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.common.Logger
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

internal class RelayConnectionHandler(
    private val connectLambda: suspend () -> Boolean,
    private val onDisconnected: suspend (exception: Throwable) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    private var connectionJob: Job? = null

    fun connect() {
        if (connectionJob?.isActive == true) return
        connectionJob = coroutineScope.launch {
            @Suppress("TooGenericExceptionCaught")
            val succeeded = try {
                connectLambda()
            } catch (ex: Exception) {
                when (ex) {
                    is CancellationException -> {
                        onDisconnected(ex)
                        throw ex
                    }
                    else -> {
                        Logger.w(TAG) { "Relay connection failed: ${ex::class.simpleName} (${ex.message})" }
                        false
                    }
                }
            }
            if (!succeeded) {
                onDisconnected(SSLException("Relay connection failed"))
            }
            connectionJob = null
        }
    }

    fun disconnect() {
        connectionJob?.cancel()
        connectionJob = null
    }

    private companion object {
        private val TAG = RelayConnectionHandler::class
    }
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :network:client:compileKotlinDesktop --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/RelayConnectionHandler.kt
git commit -m "feat: add RelayConnectionHandler"
```

---

## Task 12: NetworkClientRepository interface update

**Files:**
- Modify: `model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt`

- [ ] **Step 1: Replace discoveredServerIdsFlow with two flows**

Replace the full file content:

```kotlin
package org.vpilo.babymonitor.model.repository

import kotlinx.coroutines.flow.Flow

interface NetworkClientRepository {
    val connectionStateFlow: Flow<NetworkState>

    val serverStateFlow: Flow<ServerState>

    val localServerIdsFlow: Flow<Set<ServerId>>

    val relayServerIdsFlow: Flow<Set<ServerId>>

    suspend fun connect(server: ServerId)

    suspend fun disconnect()

    fun enableAudio(enable: Boolean)

    fun enableVideo(enable: Boolean)
}
```

- [ ] **Step 2: Compile model**

```bash
./gradlew :model:compileKotlinDesktop --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add model/src/commonMain/kotlin/org/vpilo/babymonitor/model/repository/NetworkClientRepository.kt
git commit -m "feat: split discoveredServerIdsFlow into localServerIdsFlow and relayServerIdsFlow"
```

---

## Task 13: DefaultNetworkClientRepository update

**Files:**
- Modify: `network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt`

This task rewrites `DefaultNetworkClientRepository` to expose two server flows, observe `Setting.RelayHost`, and route `connect()` to `ConnectionHandler` (LAN) or `RelayConnectionHandler` (relay).

- [ ] **Step 1: Replace DefaultNetworkClientRepository.kt**

```kotlin
package org.vpilo.babymonitor.network.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.vpilo.babymonitor.app.settings.RelayHost
import org.vpilo.babymonitor.common.Logger
import org.vpilo.babymonitor.model.CaptureMode
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerId
import org.vpilo.babymonitor.model.repository.ServerState
import org.vpilo.babymonitor.network.client.websockets.audioStreamingClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.controlClientWebSocket
import org.vpilo.babymonitor.network.client.websockets.videoStreamingClientWebSocket
import org.vpilo.babymonitor.network.common.Constants
import org.vpilo.babymonitor.network.common.DiscoveredServer
import org.vpilo.babymonitor.network.common.DiscoveryManager
import org.vpilo.babymonitor.network.common.Endpoints
import org.vpilo.babymonitor.network.common.RelayHandshake
import org.vpilo.babymonitor.network.common.deriveSecret
import org.vpilo.babymonitor.settings.model.Setting
import org.vpilo.babymonitor.settings.model.repository.SettingsRepository
import org.vpilo.babymonitor.settings.model.settings.DeviceName
import java.net.ConnectException
import java.net.InetAddress
import java.net.SocketException
import javax.net.ssl.SSLException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

private const val RELAY_PASSWORD = "babymonitor-relay-secret"

internal class DefaultNetworkClientRepository(
    discoveryManager: DiscoveryManager,
    private val dataSource: NetworkControlDataSource,
    private val settingsRepository: SettingsRepository,
    coroutineContext: CoroutineContext,
) : NetworkClientRepository {
    private val scope = CoroutineScope(SupervisorJob() + coroutineContext)

    private val networkClient: HttpClient by lazy {
        HttpClient(CIO) { install(WebSockets) }
    }

    private val relayNetworkClient: HttpClient by lazy {
        HttpClient(CIO) {
            install(WebSockets)
            engine {
                https { trustManager = RelayTrustManager() }
            }
        }
    }

    private val secret = deriveSecret(RELAY_PASSWORD)

    private val relayDiscoverySource = RelayDiscoverySource(secret)

    private val connectionState: MutableStateFlow<NetworkState> =
        MutableStateFlow(NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet))
    override val connectionStateFlow: Flow<NetworkState> = connectionState.asStateFlow()

    override val serverStateFlow: StateFlow<ServerState> = dataSource.serverState

    private val _localServers = MutableStateFlow<Set<DiscoveredServer>>(emptySet())
    override val localServerIdsFlow: Flow<Set<ServerId>> = _localServers.map { set -> set.map { it.id }.toSet() }

    override val relayServerIdsFlow: Flow<Set<ServerId>> = relayDiscoverySource.serverIds

    private var relayHost: String = ""
    private var isRelayConnection: Boolean = false
    private var currentAddress: InetAddress? = null
    private var connectedServerId: ServerId? = null
    private var isAudioEnabled: Boolean = false
    private var isVideoEnabled: Boolean = true
    private var controlConnectionHandler: ConnectionHandler? = null
    private var relayControlHandler: RelayConnectionHandler? = null
    private var audioConnectionHandler: ConnectionHandler? = null
    private var relayAudioHandler: RelayConnectionHandler? = null
    private var videoConnectionHandler: ConnectionHandler? = null
    private var relayVideoHandler: RelayConnectionHandler? = null
    private var serverStateJob: Job? = null

    init {
        settingsRepository
            .flowOf(Setting.DeviceName)
            .onEach { discoveryManager.setDeviceName(it) }
            .launchIn(scope)

        discoveryManager.discoveredServers
            .onEach { _localServers.value = it }
            .launchIn(scope)

        settingsRepository
            .flowOf(Setting.RelayHost)
            .onEach { host ->
                relayHost = host
                relayDiscoverySource.updateRelayHost(host, scope)
            }
            .launchIn(scope)
    }

    override suspend fun connect(server: ServerId) {
        val localServer = _localServers.value.firstOrNull { it.id == server }
        val isRelay = localServer == null && relayDiscoverySource.serverIds.value.contains(server)

        if (!isRelay && localServer == null) {
            Logger.w(TAG) { "Server ${server.name} not found in local or relay servers." }
            connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
            return
        }

        isRelayConnection = isRelay
        connectedServerId = server
        closeAllConnections()

        if (isRelay) {
            relayControlHandler = RelayConnectionHandler(
                coroutineScope = scope,
                connectLambda = {
                    var result = false
                    relayNetworkClient.wss(
                        method = HttpMethod.Get,
                        host = relayHost,
                        port = Constants.RELAY_PORT,
                        path = "/relay${Endpoints.CONTROL}/${server.name}",
                    ) {
                        RelayHandshake.send(this, secret)
                        onControlConnectionOpened(server, InetAddress.getByName(relayHost))
                        result = controlClientWebSocket()
                    }
                    result
                },
                onDisconnected = { onControlConnectionClosed(it) },
            ).apply { connect() }
        } else {
            val addresses = checkNotNull(localServer).addresses
            controlConnectionHandler = ConnectionHandler(
                coroutineScope = scope,
                hosts = addresses,
                connectLambda = { host ->
                    Logger.i(TAG) { "Connecting to server $server" }
                    var result = false
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = host.hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.CONTROL,
                    ) {
                        onControlConnectionOpened(server, host)
                        result = controlClientWebSocket()
                    }
                    result
                },
                onDisconnected = { onControlConnectionClosed(it) },
            ).apply { connect() }
        }

        connectionState.value = NetworkState.Connecting(server)
    }

    private fun startAudioStream() {
        val serverId = connectedServerId ?: return
        if (currentAddress == null || serverStateFlow.value.captureMode == CaptureMode.VIDEO_ONLY) return
        if (audioConnectionHandler != null || relayAudioHandler != null) {
            Logger.w(TAG) { "Audio stream is already running" }
            return
        }

        if (isRelayConnection) {
            relayAudioHandler = RelayConnectionHandler(
                coroutineScope = scope,
                connectLambda = {
                    var result = false
                    relayNetworkClient.wss(
                        method = HttpMethod.Get,
                        host = relayHost,
                        port = Constants.RELAY_PORT,
                        path = "/relay${Endpoints.STREAM_AUDIO}/${serverId.name}",
                    ) {
                        RelayHandshake.send(this, secret)
                        dataSource.setIsStreamingAudio(true)
                        result = audioStreamingClientWebSocket()
                    }
                    result
                },
                onDisconnected = {
                    dataSource.setIsStreamingAudio(false)
                    Logger.i(TAG) { "Relay audio disconnected, reconnecting" }
                    delay(1.seconds)
                    relayAudioHandler?.connect()
                },
            ).apply { connect() }
        } else {
            audioConnectionHandler = ConnectionHandler(
                coroutineScope = scope,
                hosts = setOf(checkNotNull(currentAddress)),
                connectLambda = { host ->
                    var result = false
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = host.hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.STREAM_AUDIO,
                    ) {
                        dataSource.setIsStreamingAudio(true)
                        result = audioStreamingClientWebSocket()
                    }
                    result
                },
                onDisconnected = {
                    dataSource.setIsStreamingAudio(false)
                    Logger.i(TAG) { "Audio disconnected, reconnecting" }
                    delay(1.seconds)
                    audioConnectionHandler?.connect()
                },
            ).apply { connect() }
        }
        Logger.d(TAG) { "Audio stream started" }
    }

    private fun startVideoStream() {
        val serverId = connectedServerId ?: return
        if (currentAddress == null || serverStateFlow.value.captureMode == CaptureMode.AUDIO_ONLY) return
        if (videoConnectionHandler != null || relayVideoHandler != null) {
            Logger.w(TAG) { "Video stream is already running" }
            return
        }

        if (isRelayConnection) {
            relayVideoHandler = RelayConnectionHandler(
                coroutineScope = scope,
                connectLambda = {
                    var result = false
                    relayNetworkClient.wss(
                        method = HttpMethod.Get,
                        host = relayHost,
                        port = Constants.RELAY_PORT,
                        path = "/relay${Endpoints.STREAM_VIDEO}/${serverId.name}",
                    ) {
                        RelayHandshake.send(this, secret)
                        dataSource.setIsStreamingVideo(true)
                        result = videoStreamingClientWebSocket()
                    }
                    result
                },
                onDisconnected = {
                    dataSource.setIsStreamingVideo(false)
                    Logger.i(TAG) { "Relay video disconnected, reconnecting" }
                    delay(1.seconds)
                    relayVideoHandler?.connect()
                },
            ).apply { connect() }
        } else {
            videoConnectionHandler = ConnectionHandler(
                coroutineScope = scope,
                hosts = setOf(checkNotNull(currentAddress)),
                connectLambda = { host ->
                    var result = false
                    networkClient.webSocket(
                        method = HttpMethod.Get,
                        host = host.hostAddress,
                        port = Constants.WEBSOCKET_PORT,
                        path = Endpoints.STREAM_VIDEO,
                    ) {
                        dataSource.setIsStreamingVideo(true)
                        result = videoStreamingClientWebSocket()
                    }
                    result
                },
                onDisconnected = {
                    dataSource.setIsStreamingVideo(false)
                    Logger.i(TAG) { "Video disconnected, reconnecting" }
                    delay(1.seconds)
                    videoConnectionHandler?.connect()
                },
            ).apply { connect() }
        }
        Logger.d(TAG) { "Video stream started" }
    }

    private fun stopAudioStream() {
        audioConnectionHandler?.disconnect()
        audioConnectionHandler = null
        relayAudioHandler?.disconnect()
        relayAudioHandler = null
    }

    private fun stopVideoStream() {
        videoConnectionHandler?.disconnect()
        videoConnectionHandler = null
        relayVideoHandler?.disconnect()
        relayVideoHandler = null
    }

    private fun closeAllConnections() {
        stopAudioStream()
        stopVideoStream()
        controlConnectionHandler?.disconnect()
        controlConnectionHandler = null
        relayControlHandler?.disconnect()
        relayControlHandler = null
        serverStateJob?.cancel()
        serverStateJob = null
        currentAddress = null
    }

    override fun enableAudio(enable: Boolean) {
        Logger.i(TAG) { "enableAudio: $enable" }
        isAudioEnabled = enable
        if (currentAddress == null || serverStateFlow.value.isStreamingAudio == enable) return
        if (enable) startAudioStream() else stopAudioStream()
    }

    override fun enableVideo(enable: Boolean) {
        Logger.i(TAG) { "enableVideo: $enable" }
        isVideoEnabled = enable
        if (currentAddress == null || serverStateFlow.value.isStreamingVideo == enable) return
        if (enable) startVideoStream() else stopVideoStream()
    }

    override suspend fun disconnect() {
        closeAllConnections()
        connectionState.value = NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }
    }

    private fun onControlConnectionOpened(server: ServerId, address: InetAddress) {
        currentAddress = address
        connectionState.value = NetworkState.Connected(server)
        Logger.i(TAG) { "Client state: ${connectionState.value}" }

        if (isAudioEnabled) startAudioStream()
        if (isVideoEnabled) startVideoStream()

        serverStateJob = scope.launch {
            dataSource.serverState.collect { serverState ->
                if (!serverState.isAvailable) return@collect
                Logger.i(TAG) { "Server changed capture mode: ${serverState.captureMode}" }
                when (serverState.captureMode) {
                    CaptureMode.AUDIO_ONLY -> stopVideoStream()
                    CaptureMode.VIDEO_ONLY -> stopAudioStream()
                    CaptureMode.AUDIO_AND_VIDEO -> Unit
                }
            }
        }
    }

    private fun onControlConnectionClosed(exception: Throwable) {
        closeAllConnections()
        connectionState.value = when (exception) {
            is ClosedReceiveChannelException -> return
            is ConnectException -> {
                Logger.i(TAG) { "Connection refused." }
                NetworkState.Disconnected(NetworkState.ErrorReason.ServerNotFound)
            }
            is CancellationException -> {
                Logger.i(TAG) { "Connection closed by client." }
                NetworkState.Disconnected(NetworkState.ErrorReason.ClientQuit)
            }
            is SocketException, is SSLException -> {
                Logger.i(TAG) { "Connection closed: ${exception.message}" }
                NetworkState.Disconnected(NetworkState.ErrorReason.ConnectionFailed, exception)
            }
            else -> {
                Logger.w(TAG) { "WebSocket failed: ${exception::class.simpleName} - ${exception.localizedMessage}" }
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

Note: `Setting.RelayHost` is in `appCommon` but `network:client` doesn't depend on `appCommon`. The relay host must instead be read via `SettingsRepository` with the same `SettingId("relay_host")`. Replace the `settingsRepository.flowOf(Setting.RelayHost)` line with:

```kotlin
import org.vpilo.babymonitor.model.settings.SettingId
import org.vpilo.babymonitor.settings.model.makePrimitiveSetting

// In init block, replace Setting.RelayHost with:
settingsRepository
    .flowOf(Setting.makePrimitive(id = SettingId("relay_host"), default = ""))
    .onEach { host ->
        relayHost = host
        relayDiscoverySource.updateRelayHost(host, scope)
    }
    .launchIn(scope)
```

Check what API `SettingsRepository` exposes for ad-hoc settings access — if `flowOf` requires a registered `Setting` instance, use the same approach as `AppSettings.kt` to get the exact `Setting` object. Alternatively, move `Setting.RelayHost` to `settings:model` so `network:client` can depend on it. If neither approach compiles cleanly, declare a `companion val relayHostSetting` at the top of `DefaultNetworkClientRepository.kt`:

```kotlin
private val relayHostSetting = Setting.makePrimitive(
    id = SettingId("relay_host"),
    default = "",
)
```

And use `settingsRepository.flowOf(relayHostSetting)`.

- [ ] **Step 2: Compile network:client**

```bash
./gradlew :network:client:compileKotlinDesktop --quiet
```

Fix any import or compilation errors before continuing.

- [ ] **Step 3: Compile Android**

```bash
./gradlew :network:client:compileAndroidMain --quiet
```

Expected: BUILD SUCCESSFUL on both platforms.

- [ ] **Step 4: Commit**

```bash
git add network/client/src/commonMain/kotlin/org/vpilo/babymonitor/network/client/DefaultNetworkClientRepository.kt
git commit -m "feat: wire relay discovery and routing into DefaultNetworkClientRepository"
```

---

## Task 14: CameraSelectionScreen update

**Files:**
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreenState.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreenViewModel.kt`
- Modify: `appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/CameraSelectionScreen.kt`

- [ ] **Step 1: Update CameraSelectionScreenState.kt**

```kotlin
package org.vpilo.babymonitor.app.cameraselection

import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.repository.ServerId

data class CameraSelectionScreenState(
    val networkState: NetworkState = NetworkState.Disconnected(NetworkState.ErrorReason.NotConnectedYet),
    val localServers: Set<ServerId> = emptySet(),
    val relayServers: Set<ServerId> = emptySet(),
)
```

- [ ] **Step 2: Update CameraSelectionScreenViewModel.kt**

```kotlin
package org.vpilo.babymonitor.app.cameraselection

import kotlinx.coroutines.launch
import org.vpilo.babymonitor.model.repository.NetworkClientRepository
import org.vpilo.babymonitor.model.repository.NetworkState
import org.vpilo.babymonitor.model.viewmodel.AppViewModel

class CameraSelectionScreenViewModel(
    private val networkClientRepository: NetworkClientRepository,
) : AppViewModel<CameraSelectionScreenAction, CameraSelectionScreenState, CameraSelectionScreenEffect>(
        initialState = CameraSelectionScreenState(),
    ) {
    override fun SubscriptionScope.onSubscribed() {
        networkClientRepository.localServerIdsFlow
            .subscribe { list ->
                state.copy(localServers = list).update()
            }

        networkClientRepository.relayServerIdsFlow
            .subscribe { list ->
                state.copy(relayServers = list).update()
            }

        networkClientRepository.connectionStateFlow
            .subscribe { netState ->
                state.copy(networkState = netState).update()
                if (netState is NetworkState.Connected) {
                    CameraSelectionScreenEffect.Connected.sendEffect()
                }
            }
    }

    override fun onAction(action: CameraSelectionScreenAction) {
        when (action) {
            is CameraSelectionScreenAction.ConnectToServer -> {
                vmScope.launch {
                    networkClientRepository.connect(action.server)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Update CameraSelectionScreen.kt**

Replace the `CameraSelectionScreenContent` composable and its call site. The existing `CameraSelectionScreen` composable keeps the same signature. Only `CameraSelectionScreenContent` changes — it now takes `localServers` and `relayServers` instead of a single `servers` set.

Find and replace in `CameraSelectionScreen.kt`:

Replace the call to `CameraSelectionScreenContent`:
```kotlin
        CameraSelectionScreenContent(
            modifier = modifier.fillMaxSize(),
            networkState = state.networkState,
            localServers = state.localServers,
            relayServers = state.relayServers,
            onConnectRequested = { viewModel.send(CameraSelectionScreenAction.ConnectToServer(it)) },
        )
```

Replace the `CameraSelectionScreenContent` private composable signature and body:
```kotlin
@Composable
private fun CameraSelectionScreenContent(
    modifier: Modifier = Modifier,
    networkState: NetworkState,
    localServers: Set<ServerId>,
    relayServers: Set<ServerId>,
    onConnectRequested: (server: ServerId) -> Unit,
) {
    val lazyListState = rememberLazyListState()

    Column(modifier = modifier) {
        InfoLabel(networkState)
        Spacer(modifier = Modifier.size(Theme.Paddings.Medium))

        (networkState as? NetworkState.Disconnected)
            ?.additionalInfo
            ?.let { exception ->
                Text(
                    text = "Error details: ${exception.localizedMessage}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

        LazyColumn(
            state = lazyListState,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth(fraction = .75f)
                .align(Alignment.CenterHorizontally),
        ) {
            if (localServers.isEmpty() && relayServers.isEmpty()) {
                item {
                    Text(
                        text = stringResource(Res.string.client_connection_chooser_no_servers_found),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LoadingBox()
                }
            }

            if (localServers.isNotEmpty()) {
                item {
                    Text(
                        text = "Local",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Theme.Paddings.Small),
                    )
                }
                items(items = localServers.toList()) { server ->
                    ServerButton(server, networkState, onConnectRequested)
                    Spacer(modifier = Modifier.size(Theme.Paddings.Tiny))
                }
            }

            if (relayServers.isNotEmpty()) {
                item {
                    Text(
                        text = "Remote",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Theme.Paddings.Small),
                    )
                }
                items(items = relayServers.toList()) { server ->
                    ServerButton(server, networkState, onConnectRequested)
                    Spacer(modifier = Modifier.size(Theme.Paddings.Tiny))
                }
            }
        }
    }
}

@Composable
private fun ServerButton(
    server: ServerId,
    networkState: NetworkState,
    onConnectRequested: (ServerId) -> Unit,
) {
    Button(
        enabled = networkState !is NetworkState.Connecting,
        onClick = { onConnectRequested(server) },
    ) {
        Text(
            modifier = Modifier.padding(Theme.Paddings.Small),
            text = server.name,
            style = MaterialTheme.typography.bodyMedium,
        )
        if ((networkState as? NetworkState.Connecting)?.server == server) {
            LoadingIcon()
        }
    }
}
```

Also update the `@Preview` functions to pass `localServers` / `relayServers` instead of `servers`.

- [ ] **Step 4: Compile appCommon**

```bash
./gradlew :appCommon:compileKotlinDesktop --quiet
```

Fix any compilation errors before continuing.

- [ ] **Step 5: Commit**

```bash
git add appCommon/src/commonMain/kotlin/org/vpilo/babymonitor/app/cameraselection/
git commit -m "feat: show local and relay servers in separate sections on camera selection screen"
```

---

## Task 15: Full build verification

- [ ] **Step 1: Build desktop app and relay**

```bash
./gradlew :appDesktop:desktopJar :appRelay:desktopJar --quiet
```

Expected: BUILD SUCCESSFUL for both.

- [ ] **Step 2: Build Android APK**

```bash
./gradlew :appAndroid:assembleDebug --quiet
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Smoke-test the relay**

```bash
./gradlew :appRelay:run
```

Expected: prints `Relay running. Press Enter to stop.` — relay is listening on port 47814. Press Enter to stop.

- [ ] **Step 4: Final commit**

```bash
git add -A
git status
```

Verify only expected files are staged, then:

```bash
git commit -m "feat: remote relay — full implementation complete"
```
